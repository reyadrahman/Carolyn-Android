package com.siddhantkushwaha.carolyn.index

import android.content.Context
import android.util.Log
import com.siddhantkushwaha.carolyn.common.*
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.entity.MessageThread

class Index(val context: Context) {

    private val tag: String = this::class.java.toString()
    private var subscriptions: HashMap<Int, String>? = getSubscriptions(context)

    public fun initIndex() {
        val messages = getAllSms(context)

        // add all new messages
        messages?.forEach { message ->
            indexMessage(message)
        }

        // remove deleted messages
        val realm = RealmUtil.getCustomRealmInstance(context)

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

        realm.close()
    }

    public fun indexMessage(message: Array<Any>, messageClass: String? = null): Int {
        val subscriptions = subscriptions ?: return 1

        var user2DisplayName = message[1] as String
        val timestamp = message[2] as Long
        val body = message[3] as String
        val type = message[4] as Int
        val subId = message[5] as Int

        val sent = type == 2
        val user1 = subscriptions[subId] ?: "unknown"

        user2DisplayName = normalizePhoneNumber(user2DisplayName)
        val user2 = user2DisplayName.toLowerCase()

        val id = getHash("$timestamp, $body, $sent")

        val realm = RealmUtil.getCustomRealmInstance(context)

        Log.d(tag, "Saving message $id")
        realm.executeTransaction { realmT ->

            var realmMessage = realmT.where(Message::class.java).equalTo("id", id).findFirst()
            if (realmMessage == null) {
                realmMessage = Message()
                realmMessage.body = body
                realmMessage.timestamp = timestamp
                realmMessage.sent = sent
                realmMessage.type = messageClass
                realmMessage.buildId()
            }

            var realmThread =
                realmT.where(MessageThread::class.java).equalTo("user2", user2).findFirst()
            if (realmThread == null) {
                realmThread = MessageThread()
                realmThread.user2 = user2
            }

            realmThread.user1 = user1
            realmThread.user2DisplayName = user2DisplayName

            if (realmMessage.timestamp!! > realmThread.lastMessage?.timestamp ?: 0) {
                realmThread.lastMessage = realmT.copyToRealm(realmMessage)
            }

            realmMessage.messageThread = realmThread

            realmT.insertOrUpdate(realmThread)
            realmT.insertOrUpdate(realmMessage)
        }

        realm.close()

        return 0
    }

}