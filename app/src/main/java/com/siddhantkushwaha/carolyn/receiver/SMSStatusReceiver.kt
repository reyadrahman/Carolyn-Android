package com.siddhantkushwaha.carolyn.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.Enums
import com.siddhantkushwaha.carolyn.common.util.RealmUtil
import com.siddhantkushwaha.carolyn.common.util.TelephonyUtil


class SMSStatusReceiver : BroadcastReceiver() {

    companion object {

        private val tag = "SMSStatusReceiver"

        public fun getIntent(
            context: Context,
            messageId: String,
            index: Int,
            numParts: Int,
            subId: Int,
            action: String
        ): Intent {
            val intent = Intent(context, SMSStatusReceiver::class.java)
            intent.action = action
            intent.putExtra("messageId", messageId)
            intent.putExtra("partIndex", index)
            intent.putExtra("numParts", numParts)
            intent.putExtra("subId", subId)
            return intent
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
                        if (partIndex == numParts - 1)
                            updateMessageStatusInDbs(
                                context,
                                messageId,
                                Enums.MessageStatus.sent
                            )
                    }
                    else -> {
                        Log.d(tag, "Message send failed: $messageId, $resultCode")
                        if (partIndex == numParts - 1)
                            updateMessageStatusInDbs(
                                context,
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
                        if (partIndex == numParts - 1)
                            updateMessageStatusInDbs(
                                context,
                                messageId,
                                Enums.MessageStatus.delivered
                            )
                    }
                    else -> {
                        Log.d(tag, "Message not delivered: $messageId $resultCode")
                        if (partIndex == numParts - 1)
                            updateMessageStatusInDbs(
                                context,
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
        messageId: String,
        status: String
    ) {
        val realm = RealmUtil.getCustomRealmInstance(context)
        realm.executeTransactionAsync { realmT ->
            val message = DbHelper.getMessageObject(realmT, messageId)
            if (message == null) {
                Log.e(tag, "Message object not found for message id $messageId.")
            } else {

                val smsId = message.smsId ?: return@executeTransactionAsync

                /*
                    sent intent (for message part j) ) might come up after delivered intent (for message part i) for long texts,
                    where j > i
                    therefore updateMessageStatusInDbs called for last part only, that will avoid cris - cross
                 */

                if (status == Enums.MessageStatus.notSent) {
                    val ret = TelephonyUtil.markMessageAsSendFailed(context, smsId)
                    if (ret == 0)
                        return@executeTransactionAsync
                }

                if (status == Enums.MessageStatus.sent || status == Enums.MessageStatus.delivered) {
                    val ret = TelephonyUtil.markMessageAsSendSuccess(context, smsId)
                    if (ret == 0)
                        return@executeTransactionAsync
                }

                message.status = status
                realmT.insertOrUpdate(message)
            }
        }
        realm.close()
    }
}