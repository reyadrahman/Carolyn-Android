/*
    ---- This class was created to collect data for training the neural-net ----
    ----- It will be removed in the production version on the application ------
*/

package com.siddhantkushwaha.carolyn.index

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.siddhantkushwaha.carolyn.common.*
import com.siddhantkushwaha.carolyn.common.util.CommonUtil
import com.siddhantkushwaha.carolyn.common.util.FirebaseUtil
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Message


class IndexToFirebase(private val context: Context) {

    private val firebaseDatabase = FirebaseUtil.getRealtimeDb(false)
    private val firebaseAuth = FirebaseAuth.getInstance()

    public fun upload() {

        val realm = RealmUtil.getCustomRealmInstance(context)

        val messages = realm.where(Message::class.java).findAll()
        for (message in messages) {

            val messageBody = message.body
            if (messageBody == null || message.language != Enums.LanguageType.en)
                continue

            val body = CommonUtil.cleanText(messageBody)

            val hashRatio = body.count { it == '#' } / body.length.toFloat()
            if (hashRatio < 0.5) {

                if (message.messageThread?.classifyThread() != true)
                    continue

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

        realm.close()
    }
}