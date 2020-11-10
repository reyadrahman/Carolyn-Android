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

    public fun run() {
        if (contacts == null) {
            contacts = getAllContacts(context)
        }

        if (subscriptions == null) {
            subscriptions = getSubscriptions(context)
        }

        val realm = RealmUtil.getCustomRealmInstance(context)

        indexMessages(realm)
        indexContacts(realm)

        realm.close()
    }

    private fun indexMessages(realm: Realm) {
        val messages = getAllSms(context)

        // add all new messages
        messages?.forEach { message ->
            indexMessage(realm, message)
        }

        // prune deleted messages
        realm.where(Message::class.java).findAll().forEach { message ->

            val result = messages?.find { arrMessage ->
                arrMessage[2] == message.timestamp && arrMessage[3] == message.body
            }

            if (result == null) {
                Log.d(tag, "Message deleted: ${message.body}")
                realm.executeTransaction {
                    message.deleteFromRealm()
                }
            }
        }

        // delete threads with no messages
        realm.where(MessageThread::class.java).findAll().forEach { th ->
            if (th.lastMessage == null) {
                realm.executeTransaction {
                    th.deleteFromRealm()
                }
            }
        }
    }

    private fun indexMessage(
        realm: Realm,
        message: Array<Any>,
        forceClassify: Boolean = false
    ): Int {
        val subscriptions = subscriptions ?: return 1
        val contacts = contacts ?: return 1

        val originatingAddress = message[1] as String
        val timestamp = message[2] as Long
        val body = message[3] as String
        val type = message[4] as Int
        val subId = message[5] as Int

        val sent = type == 2
        val user1 = subscriptions[subId] ?: "unknown"

        val normalizedOriginatingAddress = normalizePhoneNumber(originatingAddress)

        val user2 = normalizedOriginatingAddress ?: originatingAddress.toLowerCase()

        var contactName: String? = null
        val user2DisplayName = if (normalizedOriginatingAddress != null) {
            contactName = contacts[normalizedOriginatingAddress]
            contactName ?: normalizedOriginatingAddress
        } else {
            originatingAddress
        }

        val id = getHash("$timestamp, $body, $sent")

        Log.d(tag, "Saving message $id")
        realm.executeTransaction { realmT ->

            var realmMessage = realmT.where(Message::class.java).equalTo("id", id).findFirst()
            if (realmMessage == null) {
                realmMessage = realm.createObject(Message::class.java, id)
                    ?: throw Exception("Could not create Message object.")
                realmMessage.body = body
                realmMessage.timestamp = timestamp
                realmMessage.sent = sent
            }

            var realmThread =
                realmT.where(MessageThread::class.java).equalTo("user2", user2).findFirst()
            if (realmThread == null) {
                realmThread = realm.createObject(MessageThread::class.java, user2)
                    ?: throw Exception("Could not create Thread object.")
            }

            realmThread.user1 = user1
            realmThread.user2DisplayName = user2DisplayName
            realmThread.inContacts = contactName != null

            if (realmMessage.timestamp!! > realmThread.lastMessage?.timestamp ?: 0) {
                realmThread.lastMessage = realmMessage
            }

            if (realmThread.classifyThread() && realmMessage.sent == false) {

                if (realmMessage.type == null || forceClassify) {
                    val messageClass = MessageClassifier.doClassification(context, body, false)
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