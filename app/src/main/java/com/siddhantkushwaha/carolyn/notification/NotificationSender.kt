package com.siddhantkushwaha.carolyn.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.siddhantkushwaha.carolyn.R
import com.siddhantkushwaha.carolyn.activity.ActivityMessage


class NotificationSender(val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val notificationColor = Color.argb(255, 204, 0, 102)

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

        channel.enableLights(true)
        channel.lightColor = notificationColor

        val soundUri =
            Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.applicationContext.packageName}/raw/${channelToSound[channelId]}")
        val audioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        channel.setSound(soundUri, audioAttributes)

        notificationManager.createNotificationChannel(channel)

        return channel
    }

    private fun getActivityMessageIntent(
        notificationId: Int,
        user2: String,
        type: String?
    ): PendingIntent {
        val activityMessageIntent = Intent(context, ActivityMessage::class.java)
        activityMessageIntent.putExtra("user2", user2)
        activityMessageIntent.putExtra("view-type", type)

        return TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(activityMessageIntent)
            .getPendingIntent(notificationId, PendingIntent.FLAG_ONE_SHOT)
    }

    public fun sendNotification(
        notificationId: Int,
        user2: String,
        subject: String,
        body: String,
        smsId: Int,
        iconUri: String?,
        type: String?
    ) {

        val contentPendingIntent = getActivityMessageIntent(notificationId, user2, type)

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
            .setContentIntent(contentPendingIntent)
            .setSmallIcon(R.drawable.icon_carolyn_basic)
            .setContentTitle(subject)
            .setContentText(cleanedBody)
            .setLargeIcon(notificationIcon)
            .setStyle(notificationStyle)
            .build()

        notificationManager.notify(
            user2,
            notificationId,
            notification
        )
    }

    public fun sendNotificationOtp(
        notificationId: Int,
        user2: String,
        senderName: String,
        smsId: Int,
        otpText: String
    ) {
        val notificationLayout = RemoteViews(context.packageName, R.layout.notification_layout_otp)
        notificationLayout.setTextViewText(R.id.text_view_otp, otpText)
        notificationLayout.setTextViewText(R.id.text_view_sender, "OTP from $senderName")

        val contentPendingIntent = getActivityMessageIntent(notificationId, user2, "otp")

        val copyOtpIntent = NotificationActionReceiver.getIntent(
            context,
            NotificationActionReceiver.NotificationActionType.CopyMessage,
            smsId,
            user2
        )
        val copyOtpPendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            notificationId,
            copyOtpIntent,
            PendingIntent.FLAG_ONE_SHOT
        )
        val copyOtpAction = NotificationCompat.Action
            .Builder(R.drawable.icon_copy, "Copy", copyOtpPendingIntent).build()

        val notification = NotificationCompat.Builder(context, "otp")
            .setContentIntent(contentPendingIntent)
            .setSmallIcon(R.drawable.icon_carolyn_basic)
            .setCustomContentView(notificationLayout)
            //.addAction(copyOtpAction)
            .build()

        notificationManager.notify(
            user2,
            notificationId,
            notification
        )
    }

    public fun cancelNotificationByTag(tag: String) {
        notificationManager.activeNotifications.forEach { sbn ->
            notificationManager.cancel(tag, sbn.id)
        }
    }
}