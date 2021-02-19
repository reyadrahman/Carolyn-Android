package com.siddhantkushwaha.carolyn.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import android.util.Log
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.Enums
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil


class SMSStatusReceiver private constructor() : BroadcastReceiver() {

    companion object {

        private val tag = "SMSStatusReceiver"
        private var receiverRegistered = false

        public fun registerReceiver(context: Context) {
            if (receiverRegistered) {
                Log.d(tag, "SMSStatusReceiver is registered already, skipping.")
            } else {
                Log.d(tag, "Registering SMSStatusReceiver.")
                val smsStatusReceiver = SMSStatusReceiver()
                context.registerReceiver(
                    smsStatusReceiver,
                    IntentFilter(context.getString(R.string.action_message_status_sent))
                )
                context.registerReceiver(
                    smsStatusReceiver,
                    IntentFilter(context.getString(R.string.action_message_status_delivered))
                )
                receiverRegistered = true
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {

        val messageId = intent.extras?.getString("messageId") ?: return
        val partIndex = intent.extras?.getInt("partIndex") ?: return
        val numParts = intent.extras?.getInt("numParts") ?: return
        val subId = intent.extras?.getInt("subId") ?: return

        when (intent.action) {
            context.getString(R.string.action_message_status_sent) -> {
                Log.d(tag, "Sent intent received for message: $messageId $partIndex $numParts")
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Log.d(tag, "Message sent: $messageId")
                        updateMessageStatusInDbs(
                            context,
                            subId,
                            messageId,
                            Enums.MessageStatus.sent
                        )
                    }
                    else -> {
                        Log.d(tag, "Message send failed: $messageId, $resultCode")
                        updateMessageStatusInDbs(
                            context,
                            subId,
                            messageId,
                            Enums.MessageStatus.notSent
                        )
                    }
                }
            }

            context.getString(R.string.action_message_status_delivered) -> {
                Log.d(tag, "Pending intent received for message: $messageId $partIndex $numParts")
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Log.d(tag, "Message delivered: $messageId")
                        updateMessageStatusInDbs(
                            context,
                            subId,
                            messageId,
                            Enums.MessageStatus.delivered
                        )
                    }
                    else -> {
                        Log.d(tag, "Message not delivered: $messageId $resultCode")
                        updateMessageStatusInDbs(
                            context,
                            subId,
                            messageId,
                            Enums.MessageStatus.notSent
                        )
                    }
                }
            }
        }
    }

    private fun updateMessageStatusInDbs(
        context: Context,
        subId: Int,
        messageId: String,
        status: String
    ) {
        val realm = RealmUtil.getCustomRealmInstance(context)
        realm.executeTransactionAsync { realmT ->
            val message = DbHelper.getMessageObject(realmT, messageId)
            if (message == null) {
                Log.e(tag, "Message object not found for message id $messageId.")
            } else {

                // write in SMS provider only when not present already AND (sent OR delivered) successfully
                if (message.smsId ?: 0 < 1 && (status == Enums.MessageStatus.sent || status == Enums.MessageStatus.delivered)) {
                    val user2 = message.thread?.user2
                    val messageBody = message.body
                    val messageTimestamp = message.timestamp
                    if (user2 != null && messageBody != null && messageTimestamp != null) {
                        val smsMessage = TelephonyUtil.SMSMessage(
                            id = 0,
                            threadId = 0,
                            user2 = user2,
                            body = messageBody,
                            timestamp = messageTimestamp,
                            type = Telephony.Sms.MESSAGE_TYPE_SENT,
                            subId = subId,
                            isRead = false
                        )
                        val smsId = TelephonyUtil.saveSms(context, smsMessage)
                        if (smsId > 0)
                            message.smsId = smsId
                    }
                }

                // update in realm db
                message.status = status
                realmT.insertOrUpdate(message)
            }
        }
        realm.close()
    }
}