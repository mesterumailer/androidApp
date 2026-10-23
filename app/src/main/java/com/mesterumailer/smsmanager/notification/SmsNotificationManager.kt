package com.mesterumailer.smsmanager.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mesterumailer.smsmanager.MainActivity

object SmsNotificationManager {
    enum class PostResult {
        POSTED,
        POST_PERMISSION_DENIED,
        APP_NOTIFICATIONS_DISABLED,
        CHANNEL_DISABLED,
        POST_FAILED
    }

    const val EXTRA_ADDRESS = "sms_notification_address"
    const val EXTRA_BODY = "sms_notification_body"
    const val EXTRA_TIMESTAMP = "sms_notification_timestamp"
    const val EXTRA_CATEGORY_LABEL = "sms_notification_category_label"

    private const val CHANNEL_PREFIX = "sms_category_v3_"
    private const val LEGACY_CHANNEL_PREFIX = "sms_category_"
    private const val V2_CHANNEL_PREFIX = "sms_category_v2_"

    fun channelId(categoryId: String, soundUri: Uri?): String {
        val soundKey = soundUri?.toString()?.let { Integer.toHexString(it.hashCode()) } ?: "silent"
        return CHANNEL_PREFIX + categoryId + "_" + soundKey
    }

    private fun legacyChannelId(categoryId: String): String =
        LEGACY_CHANNEL_PREFIX + categoryId

    private fun v2ChannelId(categoryId: String): String =
        V2_CHANNEL_PREFIX + categoryId

    fun ensureChannel(
        context: Context,
        categoryId: String,
        categoryLabel: String,
        soundUri: Uri?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val targetChannelId = channelId(categoryId, soundUri)
        if (manager.getNotificationChannel(targetChannelId) == null) {
            // Android notification channel behavior is persistent. A new ID per category + sound
            // guarantees that an old silent/muted channel does not override the new selection.
            manager.notificationChannels
                .filter { it.id.startsWith(CHANNEL_PREFIX + categoryId + "_") }
                .filterNot { it.id == targetChannelId }
                .forEach { manager.deleteNotificationChannel(it.id) }
            manager.deleteNotificationChannel(v2ChannelId(categoryId))
            manager.deleteNotificationChannel(legacyChannelId(categoryId))
            manager.createNotificationChannel(
                buildChannel(categoryId, categoryLabel, soundUri, targetChannelId)
            )
        }
    }

    fun recreateChannel(
        context: Context,
        categoryId: String,
        categoryLabel: String,
        soundUri: Uri?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val targetChannelId = channelId(categoryId, soundUri)
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_PREFIX + categoryId + "_") }
            .filterNot { it.id == targetChannelId }
            .forEach { manager.deleteNotificationChannel(it.id) }
        manager.deleteNotificationChannel(v2ChannelId(categoryId))
        manager.deleteNotificationChannel(legacyChannelId(categoryId))
        manager.createNotificationChannel(
            buildChannel(categoryId, categoryLabel, soundUri, targetChannelId)
        )
    }

    fun notifyIncoming(
        context: Context,
        categoryId: String,
        categoryLabel: String,
        address: String,
        body: String,
        timestamp: Long,
        soundUri: Uri?,
        otpCode: String? = null
    ): PostResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return PostResult.POST_PERMISSION_DENIED

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return PostResult.APP_NOTIFICATIONS_DISABLED
        }

        return try {
            ensureChannel(context, categoryId, categoryLabel, soundUri)
            val targetChannelId = channelId(categoryId, soundUri)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val systemManager = context.getSystemService(NotificationManager::class.java)
                if (systemManager?.getNotificationChannel(targetChannelId)?.importance ==
                    NotificationManager.IMPORTANCE_NONE
                ) {
                    return PostResult.CHANNEL_DISABLED
                }
            }

            val notificationId = (address + "|" + timestamp + "|" + body).hashCode()
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_TIMESTAMP, timestamp)
                putExtra(EXTRA_CATEGORY_LABEL, categoryLabel)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationText = otpCode?.let { "کد تأیید: $it" } ?: preview(body)
            val expandedText = otpCode?.let { "کد تأیید: $it\n\n$body" } ?: body

            val builder = NotificationCompat.Builder(context, targetChannelId)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(address.ifBlank { "پیامک جدید" })
                .setContentText(notificationText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(false)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setVibrate(null)
                builder.setSound(soundUri)
            }

            notificationManager.notify(notificationId, builder.build())
            PostResult.POSTED
        } catch (error: Exception) {
            Log.e("SmsNotificationManager", "Unable to post incoming SMS notification", error)
            PostResult.POST_FAILED
        }
    }

    private fun buildChannel(
        categoryId: String,
        categoryLabel: String,
        soundUri: Uri?,
        channelId: String
    ): NotificationChannel =
        NotificationChannel(
            channelId,
            "پیامک‌های $categoryLabel",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "اعلان پیامک‌های دسته «$categoryLabel»"
            enableVibration(false)
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setShowBadge(true)
        }

    private fun preview(body: String): String {
        val normalized = body.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= 140) normalized else normalized.take(137) + "…"
    }
}
