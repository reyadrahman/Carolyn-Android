package com.siddhantkushwaha.carolyn.index

import android.content.Context
import android.util.Log
import com.siddhantkushwaha.carolyn.ai.MessageClassifier
import com.siddhantkushwaha.carolyn.common.*
import com.siddhantkushwaha.carolyn.entity.Contact
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.entity.MessageThread
import io.realm.Realm


class Index(private val context: Context) {

    private val tag: String = this::class.java.toString()

    private var subscriptions: HashMap<Int, String>? = null
    private var contacts: HashMap<String, String>? = null

    /*
        The whole idea of adding breakpoint feature is to make the task run faster when it inits via
        SMSReceiver class

        We can use breakpoint value to decide
            a - If Pruning should be done
            b - Where should contacts be fetched from (from db will be faster hopefully) AND if they should be indexed
            c - If only newer messages should be indexed
    */
    public fun run(breakpoint: Long = -1) {
        val realm = RealmUtil.getCustomRealmInstance(context)

        if (contacts == null) {
            contacts = getAllContacts(context)
        }

        if (subscriptions == null) {
            subscriptions = getSubscriptions(context)
        }

        indexMessages(realm, breakpoint)
        indexContacts(realm)

        realm.close()
    }

    private fun indexMessages(realm: Realm, breakpoint: Long = -1) {
        val messages = getAllSms(context)

        // prune deleted messages
        if (breakpoint == -1L) {
            val allMessages = realm.where(Message::class.java).findAll()
            allMessages.forEach { indexedMessage ->

                val result = messages?.find { message ->
                    message.timestamp == indexedMessage.timestamp && message.body == indexedMessage.body
                }

                if (result == null) {
                    Log.d(tag, "Message deleted: ${indexedMessage.body}")
                    realm.executeTransaction {
                        indexedMessage.deleteFromRealm()
                    }
                }
            }
        }

        // add all new messages
        if (messages != null) {
            for (message in messages) {
                if (message.timestamp < breakpoint) {
                    break
                }
                indexMessage(realm, message, false, breakpoint > -1L)
            }
        }

        // delete threads with no messages
        if (breakpoint == -1L) {
            val allThreads = realm.where(MessageThread::class.java).findAll()
            allThreads.forEach { th ->
                if (th.lastMessage == null) {
                    realm.executeTransaction {
                        th.deleteFromRealm()
                    }
                }
            }
        }
    }

    private fun indexMessage(
        realm: Realm,
        message: SMSMessage,
        forceClassify: Boolean = false,
        skipIfNotDownloaded: Boolean = false
    ): Int {
        val subscriptions = subscriptions ?: return 1
        val contacts = contacts ?: return 1

        val user1 = subscriptions[message.subId] ?: "unknown"

        val normalizedOriginatingAddress = normalizePhoneNumber(message.user2)
        val user2 = normalizedOriginatingAddress ?: message.user2.toLowerCase()

        var contactName: String? = null
        val user2DisplayName = if (normalizedOriginatingAddress != null) {
            contactName = contacts[normalizedOriginatingAddress]
            contactName ?: normalizedOriginatingAddress
        } else {
            message.user2
        }

        val id = getHash("${message.timestamp}, ${message.body}, ${message.sent}")
        realm.executeTransaction { realmT ->

            var realmMessage = realmT.where(Message::class.java).equalTo("id", id).findFirst()
            if (realmMessage == null) {
                realmMessage = realm.createObject(Message::class.java, id)
                    ?: throw Exception("Could not create Message object.")
                realmMessage.body = message.body
                realmMessage.timestamp = message.timestamp
                realmMessage.sent = message.sent
            }

            var realmThread =
                realmT.where(MessageThread::class.java).equalTo("user2", user2).findFirst()
            if (realmThread == null) {
                realmThread = realm.createObject(MessageThread::class.java, user2)
                    ?: throw Exception("Could not create Thread object.")
            }

            if (realmThread.user1 == null)
                realmThread.user1 = user1

            realmThread.user2DisplayName = user2DisplayName
            realmThread.inContacts = contactName != null

            if (message.timestamp > realmThread.lastMessage?.timestamp ?: 0) {
                realmThread.lastMessage = realmMessage
            }

            if (realmThread.classifyThread() && realmMessage.sent == false) {
                if (realmMessage.type == null || forceClassify) {
                    val messageClass =
                        MessageClassifier.doClassification(
                            context,
                            message.body,
                            skipIfNotDownloaded
                        )
                    if (messageClass != null)
                        realmMessage.type = messageClass
                }
            } else {
                realmMessage.type = null
            }

            realmMessage.messageThread = realmThread

            realmT.insertOrUpdate(realmThread)
            realmT.insertOrUpdate(realmMessage)
        }

        return 0
    }

    private fun indexContacts(realm: Realm) {

        contacts?.forEach { contact ->
            realm.executeTransaction { rt ->
                var realmContact =
                    realm.where(Contact::class.java).equalTo("number", contact.key).findFirst()
                if (realmContact == null) {
                    realmContact = realm.createObject(Contact::class.java, contact.key)
                        ?: throw Exception("Couldn't create contact object.")
                }
                realmContact.name = contact.value

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