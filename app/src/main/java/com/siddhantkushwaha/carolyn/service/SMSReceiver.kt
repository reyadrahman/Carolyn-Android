package com.siddhantkushwaha.carolyn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.siddhantkushwaha.carolyn.ml.MessageClassifier
import com.siddhantkushwaha.carolyn.index.IndexTask

class SMSReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val tag = this::class.java.toString()

        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {

            // save new messages in local database

            IndexTask(context, true).start()

            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                val user2 = smsMessage.originatingAddress?.replace("-", "") ?: continue

                // thread to classify and send notification
                val thread = Thread {
                    val messageClass: String? =
                        MessageClassifier.doClassification(context, smsMessage.messageBody, true)
                    // TODO send message based on notification class
                    Log.d(tag, "$messageClass")
                }

                thread.start()
            }
        }
    }
}