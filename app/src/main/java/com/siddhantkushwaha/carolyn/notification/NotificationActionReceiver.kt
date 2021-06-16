package com.siddhantkushwaha.carolyn.notification

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {

    public enum class NotificationActionType {
        CopyMessage
    }

    companion object {
        private const val KEY_ACTION_TYPE = "ACTION_TYPE"
        private const val KEY_SMS_ID = "SMS_ID"
        private const val KEY_USER2 = "USER_2"

        public fun getIntent(
            context: Context,
            actionType: NotificationActionType,
            smsId: Int,
            user2: String
        ): Intent {
            val intent = Intent(context, NotificationActionReceiver::class.java)
            intent.putExtra(KEY_ACTION_TYPE, actionType)
            intent.putExtra(KEY_SMS_ID, smsId)
            intent.putExtra(KEY_USER2, user2)
            return intent
        }
    }

    override fun onReceive(context: Context, intent: Intent) {

        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        if (remoteInput != null) {
            val actionType = intent.getSerializableExtra(KEY_ACTION_TYPE) as NotificationActionType
            when (actionType) {
                
            }
        }
    }
}