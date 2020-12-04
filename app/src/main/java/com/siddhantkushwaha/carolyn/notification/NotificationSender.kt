package com.siddhantkushwaha.carolyn.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.siddhantkushwaha.carolyn.R


class NotificationSender(val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelToName = hashMapOf(
        "otp" to "One Time Password",
        "transaction" to "Transaction",
        "update" to "Update",
        "spam" to "Spam",
        "personal" to "Personal"
    )

    private val channelToDescription = hashMapOf(
        "otp" to "This is a notification channel for OTP messages.",
        "transaction" to "This is a channel for transactional messages.",
        "update" to "This is a channel for updates.",
        "spam" to "This is channel for spam messages.",
        "personal" to "This is a channel for your personal messages."
    )

    private val channelToImportance = hashMapOf(
        "otp" to NotificationManager.IMPORTANCE_HIGH,
        "transaction" to NotificationManager.IMPORTANCE_HIGH,
        "update" to NotificationManager.IMPORTANCE_DEFAULT,
        "spam" to NotificationManager.IMPORTANCE_NONE,
        "personal" to NotificationManager.IMPORTANCE_HIGH
    )

    private val channelToSound = hashMapOf(
        "otp" to R.raw.otp,
        "transaction" to R.raw.transaction,
        "update" to R.raw.update,
        "spam" to R.raw.spam,
        "personal" to R.raw.personal
    )

    private val channelToColor = hashMapOf(
        "otp" to Color.argb(255, 204, 0, 102),
        "transaction" to Color.argb(255, 255, 102, 0),
        "update" to Color.argb(255, 255, 255, 0),
        "spam" to Color.argb(255, 255, 255, 255),
        "personal" to Color.argb(255, 153, 51, 255)
    )

    init {
        channelToDescription.keys.forEach {
            getNotificationChannel(it)
        }
    }

    private fun getNotificationChannel(channelId: String): NotificationChannel {

        val name = channelToName[channelId]
        val description = channelToDescription[channelId]
        val importance = channelToImportance[channelId]

        if (name == null || description == null || importance == null)
            throw Exception("Attributes missing for given channel.")

        val channel = NotificationChannel(channelId, name, importance)
        channel.description = description

        val channelColor = channelToColor[channelId]
        if (channelColor != null) {
            channel.enableLights(true)
            channel.lightColor = channelColor
        }

        val soundUri =
            Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.applicationContext.packageName}/raw/${channelToSound[channelId]}")
        val audioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        channel.setSound(soundUri, audioAttributes)

        notificationManager.createNotificationChannel(channel)

        return channel
    }

    public fun sendNotification(
        notificationId: Int,
        user2: String,
        subject: String,
        body: String,
        iconUri: String?,
        type: String?
    ) {

        val channelId = type ?: "personal"

        val notificationIcon = if (iconUri != null) {
            Glide.with(context)
                .asBitmap()
                .load(iconUri)
                .error(R.drawable.icon_user)
                .circleCrop()
                .submit()
                .get()
        } else {
            ContextCompat.getDrawable(context, R.drawable.icon_user)?.toBitmap()
        }

        val cleanedBody = body.trim().replace("\\s+".toRegex(), " ")

        val notificationStyle = NotificationCompat.BigTextStyle().bigText(cleanedBody)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.logo_caroyln)
            .setContentTitle(subject)
            .setContentText(cleanedBody)
            .setLargeIcon(notificationIcon)
            .setStyle(notificationStyle)
            .build()

        notificationManager.notify(user2, notificationId, notification)
    }
}