package com.siddhantkushwaha.carolyn.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class NotificationSender(val context: Context) {

    private val channelToName = hashMapOf(
        "OTP" to "One Time Password",
        "TRANSACTION" to "Transaction",
        "UPDATE" to "Update",
        "SPAM" to "Spam",
        "PERSONAL" to "Personal"
    )

    private val channelToDescription = hashMapOf(
        "OTP" to "This is a notification channel for OTP messages.",
        "TRANSACTION" to "This is a channel for transactional messages.",
        "UPDATE" to "This is a channel for updates.",
        "SPAM" to "This is channel for spam messages.",
        "PERSONAL" to "This is a channel for your personal messages."
    )

    private val channelToImportance = hashMapOf(
        "OTP" to NotificationManager.IMPORTANCE_HIGH,
        "TRANSACTION" to NotificationManager.IMPORTANCE_HIGH,
        "UPDATE" to NotificationManager.IMPORTANCE_DEFAULT,
        "SPAM" to NotificationManager.IMPORTANCE_NONE,
        "PERSONAL" to NotificationManager.IMPORTANCE_HIGH
    )

    init {
        channelToDescription.keys.forEach {
            createNotificationChannel(it)
        }
    }

    private fun createNotificationChannel(channelId: String) {

        val name = channelToName[channelId]
        val description = channelToDescription[channelId]
        val importance = channelToImportance[channelId]

        if (name == null || description == null || importance == null)
            return

        val channel = NotificationChannel(channelId, name, importance)
        channel.description = description

        val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}