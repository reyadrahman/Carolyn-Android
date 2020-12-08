package com.siddhantkushwaha.carolyn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.siddhantkushwaha.carolyn.common.LanguageType
import com.siddhantkushwaha.carolyn.common.MessageType
import com.siddhantkushwaha.carolyn.common.RealmUtil
import com.siddhantkushwaha.carolyn.entity.Contact
import com.siddhantkushwaha.carolyn.index.IndexTask
import com.siddhantkushwaha.carolyn.ml.LanguageId
import com.siddhantkushwaha.carolyn.ml.MessageClassifier
import com.siddhantkushwaha.carolyn.notification.NotificationSender

class SMSReceiver : BroadcastReceiver() {

    private val tag = "SMSReceiver"

    override fun onReceive(context: Context, intent: Intent) {

        val tag = this::class.java.toString()

        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {

            // save new messages in local database
            IndexTask(context, true).start()

            var user2 = ""
            var messageBody = ""
            var timestampMillis = -1L
            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                if (timestampMillis == -1L || timestampMillis != smsMessage.timestampMillis) {
                    if (timestampMillis > -1L)
                        processMessage(context, user2, messageBody, timestampMillis)

                    // resetting all the values
                    user2 = smsMessage.originatingAddress?.replace("-", "") ?: continue
                    messageBody = smsMessage.messageBody
                    timestampMillis = smsMessage.timestampMillis
                } else {
                    // SmsMessage is f***ing buggy, this is a workaround
                    messageBody += smsMessage.messageBody
                }
            }

            if (timestampMillis > -1L)
                processMessage(context, user2, messageBody, timestampMillis)
        }
    }

    private fun processMessage(
        context: Context,
        user2: String,
        messageBody: String,
        timestampMillis: Long
    ) {
        // thread to classify and send notification
        val thread = Thread {

            val realm = RealmUtil.getCustomRealmInstance(context)

            val contact =
                realm.where(Contact::class.java).equalTo("number", user2).findFirst()

            val messageClass =

                // If message is in contacts, always treat all messages as personal
                if (contact != null) {
                    null
                }

                // If number has 10 digits, we have decided to mark the message as personal
                else if (user2.length == 13) {
                    null
                }

                // If prediction needs to be applied
                else {

                    // If language is not english, mark it spam
                    if (LanguageId.getLanguage(messageBody) != LanguageType.en) {
                        MessageType.spam
                    }

                    // Use model
                    else {
                        MessageClassifier.doClassification(
                            context,
                            messageBody,
                            skipIfNotDownloaded = true
                        )
                    }
                }

            // Details required for sending notification
            val photoUri = contact?.photoUri
            val user2DisplayName = contact?.name ?: user2
            val trimmedMessage =
                if (messageBody.length > 300)
                    "${messageBody.substring(0, 300)}..."
                else
                    messageBody

            realm.close()

            Log.d(tag, "$messageBody - $messageClass")

            // This workaround should do for now
            val notificationId = timestampMillis.toInt()

            val notificationSender = NotificationSender(context)
            notificationSender.sendNotification(
                notificationId,
                user2,
                user2DisplayName,
                trimmedMessage,
                photoUri,
                messageClass
            )
        }

        thread.start()
    }
}