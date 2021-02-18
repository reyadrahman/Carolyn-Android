package com.siddhantkushwaha.carolyn.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.common.DbHelper
import com.siddhantkushwaha.carolyn.common.util.RealmUtil

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

        // TODO - handle all positive and negative cases
        when (intent.action) {
            context.getString(R.string.action_message_status_sent) -> {
                Log.d(tag, "Message $messageId $partIndex $numParts was sent.")
            }

            context.getString(R.string.action_message_status_delivered) -> {
                Log.d(tag, "Message $messageId $partIndex $numParts was delivered.")
            }
        }
    }

    // update internally maintained status
    private fun updateMessageStatusInRealm(context: Context, messageId: String, status: String) {
        val realm = RealmUtil.getCustomRealmInstance(context)
        realm.executeTransactionAsync { realmT ->
            val message = DbHelper.getMessageObject(realmT, messageId)
            if (message == null) {
                Log.e(tag, "Message object not found for message id $messageId.")
            } else {
                message.status = status
                realmT.insertOrUpdate(message)
            }
        }
        realm.close()
    }

    // to be done only when message sent successfully
    private fun updateInSMSProvider() {
        // TODO - implement
    }
}