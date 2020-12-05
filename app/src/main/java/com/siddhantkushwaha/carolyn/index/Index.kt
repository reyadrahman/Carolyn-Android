package com.siddhantkushwaha.carolyn.index

import android.content.Context
import android.util.Log
import com.siddhantkushwaha.carolyn.common.*
import com.siddhantkushwaha.carolyn.entity.Contact
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.entity.MessageThread
import com.siddhantkushwaha.carolyn.ml.LanguageId
import com.siddhantkushwaha.carolyn.ml.MessageClassifier
import io.realm.Realm
import io.realm.Sort
import java.io.File
import java.util.*


class Index(
    private val context: Context,
    private val optimized: Boolean
) {

    private val tag: String = this::class.java.toString()

    public fun run() {
        val realm = RealmUtil.getCustomRealmInstance(context)

        if (!optimized) {
            indexContacts(realm)
        }

        indexMessages(realm)

        realm.close()
    }

    private fun indexMessages(realm: Realm) {

        val messages = getAllSms(context)
        val subscriptions = getSubscriptions(context)

        if (messages == null || subscriptions == null)
            return

        if (!optimized) {
            pruneMessages(realm, messages)
        }

        addMessages(realm, messages, subscriptions)

        if (!optimized) {
            pruneThreads(realm)
        }
    }


    private fun pruneMessages(realm: Realm, messages: ArrayList<SMSMessage>) {
        val allMessages = realm.where(Message::class.java).findAll()

        val messageIds =
            messages.map { message ->
                getHash("${message.timestamp}, ${message.body}, ${message.sent}")
            }.toHashSet()

        allMessages.forEach { indexedMessage ->
            if (!messageIds.contains(indexedMessage.id)) {
                Log.d(tag, "Deleting message: ${indexedMessage.body}")
                realm.executeTransaction {
                    indexedMessage.deleteFromRealm()
                }
            }
        }
    }

    private fun addMessages(
        realm: Realm,
        messages: ArrayList<SMSMessage>,
        subscriptions: HashMap<Int, String>
    ) {
        val breakpoint = if (optimized) {
            realm.where(Message::class.java).sort("timestamp", Sort.DESCENDING)
                .findFirst()?.timestamp ?: -1
        } else {
            -1
        }

        for (message in messages) {
            if (message.timestamp < breakpoint) {
                break
            }

            indexMessage(realm, message, subscriptions)
        }
    }

    private fun indexMessage(
        realm: Realm,
        message: SMSMessage,
        subscriptions: HashMap<Int, String>
    ): Int {

        val user1 = subscriptions[message.subId] ?: "unknown"

        val user2 =
            normalizePhoneNumber(message.user2) ?: message.user2.toLowerCase(Locale.getDefault())

        val id = getHash("${message.timestamp}, ${message.body}, ${message.sent}")
        realm.executeTransaction { realmT ->

            var realmThread =
                realmT.where(MessageThread::class.java).equalTo("user2", user2).findFirst()
            if (realmThread == null) {
                realmThread = realm.createObject(MessageThread::class.java, user2)
                    ?: throw Exception("Could not create Thread object.")
            }

            if (realmThread.user1 == null) {
                realmThread.user1 = user1
            }

            if (realmThread.contact == null) {
                realmThread.contact =
                    realm.where(Contact::class.java).equalTo("number", user2).findFirst()
            }

            realmThread.user2DisplayName = message.user2

            var realmMessage = realmT.where(Message::class.java).equalTo("id", id).findFirst()
            if (realmMessage == null) {
                realmMessage = realm.createObject(Message::class.java, id)
                    ?: throw Exception("Could not create Message object.")
                realmMessage.body = message.body
                realmMessage.timestamp = message.timestamp
                realmMessage.sent = message.sent
                realmMessage.language = LanguageId.getLanguage(message.body)
            }

            realmMessage.smsId = message.id

            if (message.timestamp > realmThread.lastMessage?.timestamp ?: 0) {
                realmThread.lastMessage = realmMessage
            }

            if (realmMessage.sent == false) {
                // don't update status of read messages
                if (realmMessage.status != MessageStatus.read) {
                    realmMessage.status =
                        if (message.isRead) MessageStatus.read else MessageStatus.notRead
                }
            }

            /******************************** This is the real deal *******************************/

            // If message is in contacts, always treat all messages as personal
            if (realmThread.contact != null) {
                realmMessage.type = null
                realmMessage.classificationSource = SourceType.contact

            }

            // If number has 10 digits, we have decided to mark the message as personal
            else if (realmThread.user2?.length == 13) {
                realmMessage.type = null
                realmMessage.classificationSource = SourceType.numberLen
            }

            // If user is not in rules, or not in contacts, check if it is a sent message, if yes, mark as personal
            else if (realmMessage.sent == true) {
                realmMessage.type = null
                realmMessage.classificationSource = SourceType.sentMessage
            }

            // If prediction needs to be applied
            else {
                // If language is not english, mark it spam
                if (realmMessage.language != LanguageType.en) {
                    realmMessage.type = MessageType.spam
                    realmMessage.classificationSource = SourceType.model
                } else {

                    // Only try to run prediction if new message or this messages was not classified via a model last time
                    if (realmMessage.type == null || realmMessage.classificationSource != SourceType.model) {
                        val messageType = MessageClassifier.doClassification(
                            context,
                            message.body,
                            skipIfNotDownloaded = optimized
                        )

                        // messageType could be null if model was not downloaded yet and skipIfNotDownloaded was set to true
                        if (messageType != null) {
                            realmMessage.type = messageType
                            realmMessage.classificationSource = SourceType.model
                        }
                    }
                }
            }

            /******************************** ********************* *******************************/

            realmMessage.messageThread = realmThread

            realmT.insertOrUpdate(realmThread)
            realmT.insertOrUpdate(realmMessage)
        }

        return 0
    }

    private fun pruneThreads(realm: Realm) {
        val allThreads = realm.where(MessageThread::class.java).findAll()
        allThreads.forEach { th ->
            if (th.lastMessage == null) {
                realm.executeTransaction {
                    th.deleteFromRealm()
                }
            }
        }
    }

    private fun indexContacts(realm: Realm) {

        val contacts = getAllContacts(context)

        contacts?.forEach { (number, info) ->
            realm.executeTransaction { rt ->
                var realmContact =
                    realm.where(Contact::class.java).equalTo("number", number).findFirst()
                if (realmContact == null) {
                    realmContact = realm.createObject(Contact::class.java, number)
                        ?: throw Exception("Couldn't create contact object.")
                }
                realmContact.name = info.name
                realmContact.contactId = info.id

                val photoInputStream = openContactPhoto(context, info.id, true)
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