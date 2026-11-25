package com.mesterumailer.smsmanager

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import android.provider.Telephony
import android.util.Log
import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.data.SmsBlockRepository
import com.mesterumailer.smsmanager.data.SmsNotificationSettingsRepository
import com.mesterumailer.smsmanager.data.SmsOtpSettingsRepository
import com.mesterumailer.smsmanager.model.SmsCategory
import com.mesterumailer.smsmanager.notification.SmsNotificationManager
import com.mesterumailer.smsmanager.util.SmsBlockFilter
import com.mesterumailer.smsmanager.util.SmsClassifier
import com.mesterumailer.smsmanager.util.SmsNotificationPolicy
import java.util.concurrent.Executors

/**
 * Manifest-registered SMS receiver. Android can create the app process for this receiver
 * when the user has swiped the app away or the process has been reclaimed.
 *
 * goAsync() keeps the broadcast alive while disk preferences/classification/notification
 * work runs off the main thread. This does not bypass Android's explicit Force stop state.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        RECEIVER_EXECUTOR.execute {
            try {
                processIncomingSms(appContext, intent)
            } catch (error: Exception) {
                // Never let a malformed message or local settings error crash the SMS receiver.
                Log.e(TAG, "Could not process an incoming SMS broadcast", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun processIncomingSms(context: Context, intent: Intent) {
        val incoming = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (incoming.isEmpty()) return

        val address = incoming.firstOrNull()?.displayOriginatingAddress.orEmpty()
        val body = incoming.joinToString(separator = "") { it.messageBody.orEmpty() }
        val timestamp = incoming.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        if (body.isBlank()) return

        val key = (address + "|" + timestamp + "|" + body).hashCode().toString()
        if (wasProcessed(context, key)) return

        val blockSettings = SmsBlockRepository(context).getSettings()
        val isBlocked = SmsBlockFilter(
            blockSettings.blockedSenders,
            blockSettings.blockedContent
        ).isBlocked(address, body)
        if (isBlocked) {
            markProcessed(context, key)
            return
        }

        val ruleRepository = FilterRuleRepository(context)
        val rules = ruleRepository.loadRules()
        // Notification routing is intentionally independent from Inbox category activation.
        // A category may be hidden/disabled in the UI while still having its own notification enabled.
        val classifier = SmsClassifier(rules, ruleRepository.getSenderWeight())
        val analysis = classifier.analyze(address, body)
        val categoryId = analysis.categoryId
        val categoryLabel = rules.firstOrNull { it.categoryId == categoryId }?.displayName
            ?: SmsCategory.fromId(categoryId).label

        val settings = SmsNotificationSettingsRepository(context)
        if (!SmsNotificationPolicy.shouldNotify(false, settings.isEnabled(categoryId))) {
            markProcessed(context, key)
            return
        }

        val otpCode = analysis.otpCode.takeIf { categoryId == SmsCategory.OTP.id }
        if (otpCode != null && SmsOtpSettingsRepository(context).isAutoCopyEnabled()) {
            copyOtpToClipboard(context, otpCode)
        }

        SmsNotificationManager.notifyIncoming(
            context = context,
            categoryId = categoryId,
            categoryLabel = categoryLabel,
            address = address,
            body = body,
            timestamp = timestamp,
            soundUri = settings.getSoundUri(categoryId),
            otpCode = otpCode
        )

        // Record the deduplication key only after the notification path completes. If local
        // classification/settings fail, a subsequent delivery is not incorrectly suppressed.
        markProcessed(context, key)
    }

    private fun copyOtpToClipboard(context: Context, code: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("کد تأیید", code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        } else {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        runCatching { clipboard.setPrimaryClip(clip) }
    }

    private fun wasProcessed(context: Context, key: String): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return preferences.getStringSet(KEY_RECENT_KEYS, emptySet()).orEmpty().contains(key)
    }

    private fun markProcessed(context: Context, key: String) {
        synchronized(DEDUPLICATION_LOCK) {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val stored = preferences.getStringSet(KEY_RECENT_KEYS, emptySet())
                .orEmpty()
                .toMutableSet()
            if (stored.add(key)) {
                preferences.edit().putStringSet(
                    KEY_RECENT_KEYS,
                    stored.toList().takeLast(MAX_RECENT_KEYS).toSet()
                ).apply()
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
        private const val PREFERENCES_NAME = "sms_receiver_state"
        private const val KEY_RECENT_KEYS = "recent_message_keys"
        private const val MAX_RECENT_KEYS = 64
        private val DEDUPLICATION_LOCK = Any()
        private val RECEIVER_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "sms-notification-receiver").apply { isDaemon = true }
        }
    }
}
