package com.siddhantkushwaha.carolyn.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationActionReceiver : BroadcastReceiver() {

    public enum class NotificationActionType {
        CopyMessage,
        CutMessage
    }

    companion object {
        private const val KEY_ACTION_TYPE = "ACTION_TYPE"
        private const val KEY_NOTIFICATION_ID = "NOTIFICATION_ID"
        private const val KEY_SMS_ID = "SMS_ID"
        private const val KEY_USER2 = "USER_2"

        public fun getIntent(
            context: Context,
            actionType: NotificationActionType,
            notificationId: Int,
            smsId: Int,
            user2: String,
            action: String
        ): Intent {
            val intent = Intent(context, NotificationActionReceiver::class.java)
            intent.action = action
            intent.putExtra(KEY_ACTION_TYPE, actionType)
            intent.putExtra(KEY_NOTIFICATION_ID, notificationId)
            intent.putExtra(KEY_SMS_ID, smsId)
            intent.putExtra(KEY_USER2, user2)
            return intent
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val actionType = intent.getSerializableExtra(KEY_ACTION_TYPE) as NotificationActionType
        val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, 0)
        val smsId = intent.getIntExtra(KEY_SMS_ID, -1)
        val user2 = intent.getStringExtra(KEY_USER2)

        if (notificationId == 0 || user2 == null)
            return

        Log.d(javaClass.toString(), "Notification action received : $actionType")
        when (actionType) {
            NotificationActionType.CopyMessage -> {
                Log.d(javaClass.toString(), "Copy action received.")
            }
        }

        Log.d(javaClass.toString(), "Cancelling notification: $notificationId")

        val ns = NotificationSender(context)
        ns.cancelNotificationByTagAndId(user2, notificationId)
    }

}