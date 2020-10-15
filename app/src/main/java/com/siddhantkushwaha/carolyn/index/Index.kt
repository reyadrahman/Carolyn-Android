package com.siddhantkushwaha.carolyn.index

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.siddhantkushwaha.carolyn.FirebaseUtils
import com.siddhantkushwaha.carolyn.activity.ActivityBase
import com.siddhantkushwaha.carolyn.common.*
import com.siddhantkushwaha.carolyn.entity.Message
import com.siddhantkushwaha.carolyn.entity.MessageThread

class Index(private val activity: ActivityBase) {

    private val TAG: String = this::class.java.toString()

    private val firebaseDatabase = FirebaseUtils.getRealtimeDb(false)
    private val firebaseAuth = FirebaseAuth.getInstance()

    public fun initIndex() {
        getAllContacts(activity) { contacts ->
            getSubscriptions(activity) { subscriptions ->
                getAllSms(activity) { messages ->
                    saveToRealm(contacts, subscriptions, messages)
                    uploadToFirebase(contacts)
                }
            }
        }
    }

    private fun uploadToFirebase(contacts: HashMap<String, String>) {
        val realm = RealmUtil.getCustomRealmInstance(activity)

        realm.where(Message::class.java).findAll().forEach { message ->
            val body = cleanText(message.body!!)

            // validate message body
            if (body.count { it == '#' } / body.length.toFloat() < 0.5) {
                // if message is not in contacts
                if (!contacts.containsKey(message.messageThread!!.user2!!)) {
                    val data = HashMap<String, String>()

                    data["userId"] = firebaseAuth.currentUser?.email ?: "unknown"
                    data["messageId"] = message.id!!
                    data["user1"] = message.messageThread!!.user1!!
                    data["user2"] = message.messageThread!!.user2!!
                    data["timestamp"] = "${message.timestamp!!}"
                    data["body"] = body

                    firebaseDatabase.getReference("messages/${message.id!!}").setValue(data)
                }
            }
        }

        realm.close()
    }

    private fun saveToRealm(
            contacts: HashMap<String, String>,
            subscriptions: HashMap<Int, String>,
            messages: ArrayList<Array<Any>>,
    ) {

        contacts.forEach { contact ->
            Log.d(TAG, contact.toString())
        }

        subscriptions.forEach { subscription ->
            Log.d(TAG, subscription.toString())
        }

        val realm = RealmUtil.getCustomRealmInstance(activity)

        // save all messages/build threads and save to realm
        messages.forEach { message ->
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

            Log.d(TAG, "Saving message $id")
            realm.executeTransaction { realmT ->

                var realmMessage = realmT.where(Message::class.java).equalTo("id", id).findFirst()
                if (realmMessage == null) {
                    realmMessage = Message()
                    realmMessage.body = body
                    realmMessage.timestamp = timestamp
                    realmMessage.sent = sent
                    realmMessage.buildId()
                }

                var realmThread = realmT.where(MessageThread::class.java).equalTo("user2", user2).findFirst()
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
        }

        realm.close()
    }
}