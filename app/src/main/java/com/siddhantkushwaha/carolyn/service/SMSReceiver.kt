package com.siddhantkushwaha.carolyn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Contact
import com.siddhantkushwaha.carolyn.index.IndexTask
import com.siddhantkushwaha.carolyn.ml.MessageClassifier
import com.siddhantkushwaha.carolyn.notification.NotificationSender

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

                    val realm = RealmUtil.getCustomRealmInstance(context)

                    val contact =
                        realm.where(Contact::class.java).equalTo("number", user2).findFirst()

                    val messageClass: String? = if (contact == null)
                        MessageClassifier.doClassification(context, smsMessage.messageBody, true)
                    else null

                    val user2DisplayName = contact?.name ?: user2

                    Log.d(tag, "${smsMessage.messageBody} - $messageClass")

                    realm.close()

                    val notificationSender = NotificationSender(context)
                    notificationSender.sendNotification(
                        user2,
                        user2DisplayName,
                        smsMessage.messageBody,
                        contact?.photoUri,
                        messageClass
                    )
                }

                thread.start()
            }
        }
    }
}