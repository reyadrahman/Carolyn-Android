package com.siddhantkushwaha.carolyn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.siddhantkushwaha.carolyn.activity.ActivityMessage
import com.siddhantkushwaha.carolyn.common.ActivityTracker
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.Enums
import com.siddhantkushwaha.carolyn.common.Helper
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil
import com.siddhantkushwaha.carolyn.index.Index
import com.siddhantkushwaha.carolyn.ml.LanguageId
import com.siddhantkushwaha.carolyn.ml.MessageClassifier
import com.siddhantkushwaha.carolyn.notification.NotificationSender
import java.time.Instant
import java.util.*


class SMSReceiver : BroadcastReceiver() {

    private val tag = "SMSReceiver"

    companion object {

        @JvmStatic
        private val index = Index(true)
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {

            val extras = intent.extras ?: return
            val subscription = extras.getInt("subscription")

            val messagesMap = HashMap<String, TelephonyUtil.SMSMessage>()

            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                val originatingAddress = smsMessage.originatingAddress!!
                if (!messagesMap.containsKey(originatingAddress)) {
                    val messageDetails = TelephonyUtil.SMSMessage(
                        id = 0,
                        threadId = 0,
                        user2 = originatingAddress,
                        timestamp = Instant.now().toEpochMilli(),
                        body = smsMessage.messageBody,
                        type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                        subId = subscription,
                        isRead = false
                    )
                    messagesMap[originatingAddress] = messageDetails
                } else {
                    val oldMessageDetails = messagesMap[originatingAddress]
                        ?: throw Exception("Old message details not found.")
                    val newMessageDetails = TelephonyUtil.SMSMessage(
                        id = oldMessageDetails.id,
                        threadId = oldMessageDetails.threadId,
                        user2 = oldMessageDetails.user2,
                        timestamp = oldMessageDetails.timestamp,
                        body = oldMessageDetails.body + smsMessage.messageBody,
                        type = oldMessageDetails.type,
                        subId = oldMessageDetails.subId,
                        isRead = oldMessageDetails.isRead
                    )
                    messagesMap[originatingAddress] = newMessageDetails
                }
            }

            messagesMap.forEach { (_, details) ->

                if (TelephonyUtil.isDefaultSmsApp(context)) {
                    // we need to save messages manually when our app is default
                    TelephonyUtil.saveSms(context, details)
                }

                processMessage(context, details)
            }

            // ********* Can't use IndexTask here because it only runs one instance at a time
            // ********* if multiple messages received simultaneously, data will not be show immediately
            val indexThread = Thread {
                index.run(context)
            }
            indexThread.start()
        }
    }

    private fun processMessage(
        context: Context,
        message: TelephonyUtil.SMSMessage
    ) {
        val thread = Thread {
            val realm = RealmUtil.getCustomRealmInstance(context)

            // decide and send notification if needed
            val user2 = Helper.normalizeUser2(message.user2)
            val isMessageActivityForCurrentUserActive =
                ActivityTracker.getActivityName() == ActivityMessage::class.java.toString()
                        && ActivityTracker.getActivityExtras()?.get("user2") == user2

            if (!isMessageActivityForCurrentUserActive) {
                Log.d(tag, "Activity not active for user $user2, sending notification.")

                val contact = DbHelper.getContactObject(realm, user2)
                val rule = DbHelper.getRuleObject(realm, user2)

                val messageClass =

                    // rule has the highest priority
                    if (rule != null) {
                        rule.type
                    }

                    // If message is in contacts, always treat all messages as personal
                    else if (contact != null) {
                        null
                    }

                    // If number has 10 digits and classification not enabled on unsaved numbers,
                    // we have decided to mark the message as personal
                    else if (user2.length == 13 && !DbHelper.getUnsavedNumberClassificationRule(
                            context
                        )
                    ) {
                        null
                    }

                    // If prediction needs to be applied
                    else {

                        // If language is not english, mark it spam
                        if (LanguageId.getLanguage(message.body) != Enums.LanguageType.en) {
                            Enums.MessageType.spam
                        }

                        // Use model
                        else {
                            MessageClassifier.doClassification(
                                context,
                                message.body,
                                skipIfNotDownloaded = true
                            )
                        }
                    }

                // Details required for sending notification
                val photoUri = contact?.photoUri
                val user2DisplayName = contact?.name ?: contact?.number ?: message.user2

                Log.d(tag, "${message.body} - $messageClass")

                // This workaround should do for now
                val notificationId = message.timestamp.toInt()

                val notificationSender = NotificationSender(context)


                var sent = false
                when (messageClass) {
                    Enums.MessageType.otp -> {
                        val otp = Helper.fetchOtpFromText(message.body)
                        if (otp != null) {
                            sent = true
                            notificationSender.sendNotificationOtp(
                                notificationId,
                                user2,
                                user2DisplayName,
                                otp
                            )
                        }
                    }
                }

                if (!sent) {
                    sent = true
                    notificationSender.sendNotification(
                        notificationId,
                        user2,
                        user2DisplayName,
                        message.body,
                        photoUri,
                        messageClass
                    )
                }
            }

            realm.close()
        }
        thread.start()
    }
}