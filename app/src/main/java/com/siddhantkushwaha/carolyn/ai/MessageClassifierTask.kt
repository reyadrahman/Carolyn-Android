package com.siddhantkushwaha.carolyn.ai

import android.app.Activity
import android.util.Log
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Message

class MessageClassifierTask(
    private val activity: Activity,
    private val messages: ArrayList<Pair<String, String>>
) : Thread() {

    companion object {
        private val tag = "MessageClassifierTask"

        @JvmStatic
        private var inProgress = false

        @JvmStatic
        private var messageClassifier: MessageClassifier? = null
    }

    private val taskId = ('a'..'z').shuffled().take(6).joinToString("")


    override fun run() {

        if (inProgress) {
            Log.d("$tag-$taskId", "Another instance of this task is running, skip.")
            return
        }

        Log.d("$tag-$taskId", "Started classifying messages.")

        /* mark as true so that another task does not do the same task */
        inProgress = true

        if (messageClassifier == null) {
            Log.d("$tag-$taskId", "Initializing MessageClassifier.")
            messageClassifier = MessageClassifier.getInstance(activity)
        }

        val realm = RealmUtil.getCustomRealmInstance(activity)

        messages.forEach { message ->
            val messageClass = messageClassifier?.doClassification(message.second)
            if (messageClass != null) {
                realm.executeTransaction { realmT ->
                    val messageL =
                        realmT.where(Message::class.java).equalTo("id", message.first).findFirst()
                    if (messageL != null) {
                        messageL.type = messageClass
                        realmT.insertOrUpdate(messageL)
                    }
                }
            }
        }

        realm.close()

        /* clear the flag */
        inProgress = false

        Log.d("$tag-$taskId", "Finished classifying messages.")
    }
}