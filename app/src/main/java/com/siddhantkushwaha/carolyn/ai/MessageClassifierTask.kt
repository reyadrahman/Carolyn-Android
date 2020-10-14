package com.siddhantkushwaha.carolyn.ai

import android.app.Activity
import android.util.Log
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Message
import io.realm.Realm

class MessageClassifierTask(
        private val activity: Activity,
        private val messages: ArrayList<Pair<String, String>>
) : Thread() {

    companion object {
        private val tag = "MessageClassifierTask"

        @JvmStatic
        private var inProgress = false
    }

    private var realm: Realm? = null
    private var messageClassifier: MessageClassifier? = null

    override fun run() {

        if (inProgress) {
            Log.i(tag, "Another instance of this task in doing classification, skip.")
            return
        }

        Log.i(tag, "Started classifying messages.")

        /* mark as true so that another task does not do the same task */
        inProgress = true

        if (messageClassifier == null) {
            Log.i(tag, "Initializing MessageClassifier.")
            messageClassifier = MessageClassifier.getInstance(activity)
        }

        if (realm == null) {
            Log.i(tag, "Initializing Realm.")
            realm = RealmUtil.getCustomRealmInstance(activity)
        }

        messages.forEach { message ->
            val messageClass = messageClassifier?.doClassification(message.second)
            if (messageClass != null) {
                realm?.executeTransaction { realmT ->
                    val messageL = realmT.where(Message::class.java).equalTo("id", message.first).findFirst()
                    if (messageL != null) {
                        messageL.type = messageClass
                        realmT.insertOrUpdate(messageL)
                    }
                }
            }
        }


        /* clear the flag */
        inProgress = false

        Log.i(tag, "Finished classifying messages.")
    }
}