package com.siddhantkushwaha.carolyn.index

import android.content.Context
import android.util.Log
import com.siddhantkushwaha.carolyn.ml.MessageClassifier
import com.siddhantkushwaha.carolyn.common.*
import com.siddhantkushwaha.carolyn.entity.Contact
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.entity.MessageThread
import com.siddhantkushwaha.carolyn.ml.LanguageId
import io.realm.Realm
import io.realm.Sort
import java.util.*
import kotlin.collections.HashMap


class Index(
    private val context: Context,
    private val optimized: Boolean = false
) {

    private val tag: String = this::class.java.toString()

    private var subscriptions: HashMap<Int, String>? = null
    private var contacts: HashMap<String, String>? = null

    public fun run() {
        val realm = RealmUtil.getCustomRealmInstance(context)

        if (contacts == null) {
            // TODO - if optimized, get contacts from DB instead
            contacts = getAllContacts(context)
        }

        if (subscriptions == null) {
            subscriptions = getSubscriptions(context)
        }

        indexMessages(realm)

        if (!optimized) {
            indexContacts(realm)
        }

        realm.close()
    }

    private fun indexMessages(realm: Realm) {
        val messages = getAllSms(context)

        // prune deleted messages
        if (!optimized) {
            val allMessages = realm.where(Message::class.java).findAll()
            allMessages.forEach { indexedMessage ->

                val result = messages?.find { message ->
                    message.timestamp == indexedMessage.timestamp && message.body == indexedMessage.body
                }

                if (result == null) {
                    Log.d(tag, "Deleting message: ${indexedMessage.body}")
                    realm.executeTransaction {
                        indexedMessage.deleteFromRealm()
                    }
                }
            }
        }

        // add all new messages
        val breakpoint =
            if (optimized) {
                realm.where(Message::class.java).sort("timestamp", Sort.DESCENDING)
                    .findFirst()?.timestamp ?: -1
            } else {
                -1
            }

        if (messages != null) {
            for (message in messages) {
                if (message.timestamp < breakpoint) {
                    break
                }
                Log.d(
                    tag,
                    "Indexing message: ${message.body} ${LanguageId.getLanguage(message.body)}"
                )
                indexMessage(realm, message)
            }
        }

        // delete threads with no messages
        if (!optimized) {
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
        message: SMSMessage
    ): Int {
        val subscriptions = subscriptions ?: return 1
        val contacts = contacts ?: return 1

        val user1 = subscriptions[message.subId] ?: "unknown"

        val normalizedOriginatingAddress = normalizePhoneNumber(message.user2)
        val user2 = normalizedOriginatingAddress ?: message.user2.toLowerCase(Locale.getDefault())

        var contactName: String? = null
        val user2DisplayName = if (normalizedOriginatingAddress != null) {
            contactName = contacts[normalizedOriginatingAddress]
            contactName ?: normalizedOriginatingAddress
        } else {
            message.user2
        }

        val id = getHash("${message.timestamp}, ${message.body}, ${message.sent}")
        realm.executeTransaction { realmT ->

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

            var realmMessage = realmT.where(Message::class.java).equalTo("id", id).findFirst()
            if (realmMessage == null) {
                realmMessage = realm.createObject(Message::class.java, id)
                    ?: throw Exception("Could not create Message object.")
                realmMessage.body = message.body
                realmMessage.timestamp = message.timestamp
                realmMessage.sent = message.sent
                realmMessage.language = LanguageId.getLanguage(message.body)
            }

            if (message.timestamp > realmThread.lastMessage?.timestamp ?: 0) {
                realmThread.lastMessage = realmMessage
            }

            if (realmThread.classifyThread() && realmMessage.sent == false) {
                if (realmMessage.type == null) {
                    val messageClass =
                        MessageClassifier.doClassification(context, message.body, optimized)
                    if (messageClass != null)
                        realmMessage.type = messageClass
                } else {
                    if (realmMessage.language != "en") {
                        realmMessage.type = "spam"
                    }
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