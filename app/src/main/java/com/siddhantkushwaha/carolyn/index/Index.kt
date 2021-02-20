package com.siddhantkushwaha.carolyn.index

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.Enums
import com.siddhantkushwaha.carolyn.common.util.CommonUtil
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil
import com.siddhantkushwaha.carolyn.entity.Contact
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.entity.MessageThread
import com.siddhantkushwaha.carolyn.ml.LanguageId
import com.siddhantkushwaha.carolyn.ml.MessageClassifier
import io.realm.Realm
import java.io.File
import java.time.Instant
import java.util.*


class Index(private val optimized: Boolean) {

    private val tag: String = "Index"

    public fun run(context: Context) {
        val indexingStartTimeMillis = Instant.now().toEpochMilli()

        val realm = RealmUtil.getCustomRealmInstance(context)

        if (!optimized) indexContacts(context, realm)
        fetchAndIndexMessages(context, realm, indexingStartTimeMillis)

        realm.close()
    }

    private fun fetchAndIndexMessages(
        context: Context,
        realm: Realm,
        indexingStartTimeMillis: Long
    ) {

        // only index messages from last 1 hour if optimized version is running
        val fromTimeMillis = if (!optimized) -1 else indexingStartTimeMillis - (1 * 60 * 60 * 1000)

        val messages = TelephonyUtil.getAllSms(context, fromTimeMillis)

        val subscriptions = TelephonyUtil.getSubscriptions(context)

        if (messages == null || subscriptions == null)
            return

        Log.d(tag, "Number of messages after $fromTimeMillis: ${messages.size}")

        // index from sms provider to realm db
        messages.forEach { message ->
            indexMessage(context, realm, message, subscriptions)
        }

        // if fromTime is -1, when not optimized, then all messages will be here
        // be careful, pruneMessage should get all messages, otherwise older messages will start disappearing !!
        if (!optimized) {
            pruneMessages(realm, messages, indexingStartTimeMillis)
            pruneThreads(realm)
            if (TelephonyUtil.isDefaultSmsApp(context))
                markSmsReadInContentProvider(context, realm)
        }
    }

    private fun pruneMessages(
        realm: Realm,
        messages: ArrayList<TelephonyUtil.SMSMessage>,
        indexingStartTimeMillis: Long
    ) {
        val allMessages = realm.where(Message::class.java).findAll()
        val messageIds = messages.map { message ->
            DbHelper.getMessageId(message.timestamp, message.body)
        }.toHashSet()

        for (indexedMessage in allMessages) {

            val shouldBePruned = !messageIds.contains(indexedMessage.id)
                    && indexedMessage.status != Enums.MessageStatus.notSent
                    && indexedMessage.status != Enums.MessageStatus.pending
                    // ***** message should be older than when indexing started ****
                    // case where new message came in after indexing started (so the message won't be in messages list)
                    // it will deleted, and added again in second fetch, lol, like a glitch
                    && indexedMessage.timestamp ?: 0 < indexingStartTimeMillis

            if (shouldBePruned) {
                Log.d(tag, "Deleting message: ${indexedMessage.body}")
                realm.executeTransaction {
                    indexedMessage.deleteFromRealm()
                }
            }
        }
    }

    private fun pruneThreads(realm: Realm) {
        val allThreads = realm.where(MessageThread::class.java).findAll()
        for (thread in allThreads) {
            if (thread.numMessages() < 1) {
                realm.executeTransaction {
                    thread.deleteFromRealm()
                }
            }
        }
    }

    private fun indexMessage(
        context: Context,
        realm: Realm,
        message: TelephonyUtil.SMSMessage,
        subscriptions: HashMap<Int, TelephonyUtil.SubscriptionInfo>
    ): Int {
        val user1 = subscriptions[message.subId]?.number ?: "UNKNOWN"
        val user2 = CommonUtil.normalizePhoneNumber(message.user2)
            ?: message.user2.replace("-", "").toLowerCase(Locale.getDefault())

        realm.executeTransaction { realmT ->
            val realmThread = DbHelper.getOrCreateThreadObject(realmT, user2)
            if (realmThread.contact == null)
                realmThread.contact = DbHelper.getContactObject(realmT, user2)

            realmThread.user2DisplayName = message.user2

            val messageId = DbHelper.getMessageId(message.timestamp, message.body)
            var realmMessage = DbHelper.getMessageObject(realmT, messageId)
            if (realmMessage == null) {
                realmMessage = DbHelper.createMessageObject(realmT, messageId)
                realmMessage.body = message.body
                realmMessage.timestamp = message.timestamp
                realmMessage.smsType = message.type
                realmMessage.language = LanguageId.getLanguage(message.body)
            }

            realmMessage.smsId = message.id

            // TODO - sub id might change when sims are changed, workaround for now, don't overwrite
            if (realmMessage.user1 == null)
                realmMessage.user1 = user1

            // received sms
            if (realmMessage.smsType == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                // don't update status of read messages
                if (realmMessage.status != Enums.MessageStatus.read) {
                    realmMessage.status =
                        if (message.isRead) Enums.MessageStatus.read else Enums.MessageStatus.notRead
                }
            }
            // sent sms
            else {
                // if present here, assume sent
                // if marked as delivered, leave as is, that's even better!
                if (realmMessage.status != Enums.MessageStatus.delivered)
                    realmMessage.status = Enums.MessageStatus.sent
            }

            if (message.timestamp > realmThread.timestamp ?: 0)
                realmThread.timestamp = message.timestamp

            /******************************** This is the real deal *******************************/

            // find the rule
            val rule = DbHelper.getRuleObject(realmT, user2)
            if (rule != null) {
                realmMessage.type = rule.type
                realmMessage.classificationSource = Enums.SourceType.rule
            }

            // If message is in contacts, always treat all messages as personal
            else if (realmThread.contact != null) {
                realmMessage.type = null
                realmMessage.classificationSource = Enums.SourceType.contact
            }

            // If number has 10 digits and classification not enabled on unsaved numbers,
            // we have decided to mark the message as personal
            else if (realmThread.user2?.length == 13
                && !DbHelper.getUnsavedNumberClassificationRule(context)
            ) {
                realmMessage.type = null
                realmMessage.classificationSource = Enums.SourceType.unsavedNumber
            }

            // If user is not in rules, or not in contacts, check if it is a sent message, if yes, mark as personal
            else if (realmMessage.smsType != Telephony.Sms.MESSAGE_TYPE_INBOX) {
                realmMessage.type = null
                realmMessage.classificationSource = Enums.SourceType.sentMessage
            }

            // If prediction needs to be applied
            else {
                // If language is not english, mark it spam
                if (realmMessage.language != Enums.LanguageType.en) {
                    realmMessage.type = Enums.MessageType.spam
                    realmMessage.classificationSource = Enums.SourceType.model
                } else {

                    // Only try to run prediction if new message or this messages was not classified via a model last time
                    if (realmMessage.type == null || realmMessage.classificationSource != Enums.SourceType.model) {
                        val messageType = MessageClassifier.doClassification(
                            context,
                            message.body,
                            skipIfNotDownloaded = optimized
                        )

                        // messageType could be null if model was not downloaded yet and skipIfNotDownloaded was set to true
                        if (messageType != null) {
                            realmMessage.type = messageType
                            realmMessage.classificationSource = Enums.SourceType.model
                        }
                    }
                }
            }

            /******************************** ********************* *******************************/

            realmMessage.thread = realmThread

            realmT.insertOrUpdate(realmThread)
            realmT.insertOrUpdate(realmMessage)
        }

        return 0
    }

    private fun markSmsReadInContentProvider(context: Context, realm: Realm) {
        val messages = realm.where(Message::class.java).findAll()
        for (message in messages) {
            val id = message.smsId
            if (id != null && message.smsType == Telephony.Sms.MESSAGE_TYPE_INBOX && message.status == Enums.MessageStatus.read) {
                val ret = TelephonyUtil.markSmsRead(context, id)
                if (!ret)
                    break
            }
        }
    }

    private fun indexContacts(context: Context, realm: Realm) {
        val contacts = TelephonyUtil.getAllContacts(context)
        contacts?.forEach { (number, info) ->
            realm.executeTransaction { rt ->
                val realmContact = DbHelper.getOrCreateContactObject(rt, number)

                realmContact.name = info.name
                realmContact.contactId = info.id

                val photoInputStream = TelephonyUtil.openContactPhoto(context, info.id, true)
                if (photoInputStream != null) {

                    val photoBytes = photoInputStream.readBytes()
                    photoInputStream.close()
                    val photoId = info.id

                    val externalStorage = context.getExternalFilesDir(null)

                    if (externalStorage != null) {
                        val file = File(externalStorage, "$photoId.jpg")
                        file.writeBytes(photoBytes)

                        realmContact.photoUri = file.toURI().toString()
                    }
                } else {
                    realmContact.photoUri = null
                }

                rt.insertOrUpdate(realmContact)
            }
        }

        realm.where(Contact::class.java).findAll().forEach { ct ->
            if (contacts?.containsKey(ct.number) == false) {
                realm.executeTransaction {
                    ct.deleteFromRealm()
                }
            }
        }
    }
}