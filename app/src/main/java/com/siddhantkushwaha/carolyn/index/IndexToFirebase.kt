/*
    ---- This class was created to collect data for training the neural-net ----
    ----- It will be removed in the production version on the application ------
*/

package com.siddhantkushwaha.carolyn.index

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.siddhantkushwaha.carolyn.common.FirebaseUtils
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.common.cleanText
import com.siddhantkushwaha.carolyn.common.getAllContacts
import com.siddhantkushwaha.carolyn.entity.Message

class IndexToFirebase(private val activity: Activity) {

    private val firebaseDatabase = FirebaseUtils.getRealtimeDb(false)
    private val firebaseAuth = FirebaseAuth.getInstance()

    public fun upload() {
        val contacts = getAllContacts(activity) ?: return

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
}