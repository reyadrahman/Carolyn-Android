package com.siddhantkushwaha.carolyn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.siddhantkushwaha.carolyn.index.Index


class SMSReceiver : BroadcastReceiver() {


    override fun onReceive(context: Context, intent: Intent) {

        val tag = this::class.java.toString()

        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION == intent.action) {

            val index = Index(context)

            val subId =
                if (intent.extras?.keySet()?.find { it == Telephony.Sms.SUBSCRIPTION_ID } != null) {
                    intent.extras?.getInt(Telephony.Sms.SUBSCRIPTION_ID) ?: -1
                } else {
                    intent.extras?.getInt("sim_id") ?: -1
                }

            for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {

                val message = Array<Any>(6) { 0 }
                message[0] = -1
                message[1] = smsMessage.originatingAddress ?: ""
                message[2] = smsMessage.timestampMillis
                message[3] = smsMessage.messageBody
                message[4] = 1
                message[5] = subId

                Log.d(tag, "Received new message -")
                message.forEach {
                    Log.d(tag, "$it")
                }

                var err = index.indexMessage(message)
            }
        }

    }

}