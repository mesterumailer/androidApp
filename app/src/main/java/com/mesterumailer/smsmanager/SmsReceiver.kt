package com.mesterumailer.smsmanager

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.provider.Telephony
import android.util.Log
import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.data.SmsBlockRepository
import com.mesterumailer.smsmanager.data.SmsReceiverDiagnosticsRepository
import com.mesterumailer.smsmanager.data.SmsNotificationSettingsRepository
import com.mesterumailer.smsmanager.util.SmsNotificationPolicy
import com.mesterumailer.smsmanager.data.SmsOtpSettingsRepository
import com.mesterumailer.smsmanager.data.SmsReceiverDiagnosticStatus
import com.mesterumailer.smsmanager.model.SmsCategory
import com.mesterumailer.smsmanager.notification.SmsNotificationManager
import com.mesterumailer.smsmanager.util.SmsBlockFilter
import com.mesterumailer.smsmanager.util.SmsClassifier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Receiver work includes classification, local preference reads and notification-channel
        // checks. Keep it off the main thread, but use goAsync so Android knows the broadcast is
        // still being processed until the notification decision has completed.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        RECEIVER_EXECUTOR.execute {
            try {
                processIncomingSms(appContext, intent)
            } catch (error: Exception) {
                Log.e(TAG, "Background SMS processing failed", error)
                runCatching {
                    SmsReceiverDiagnosticsRepository(appContext).record(
                        SmsReceiverDiagnosticStatus.PROCESSING_ERROR,
                        detailCode = error.javaClass.simpleName
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun processIncomingSms(context: Context, intent: Intent) {
        val diagnostics = SmsReceiverDiagnosticsRepository(context)
        diagnostics.record(SmsReceiverDiagnosticStatus.RECEIVER_STARTED)

        val incoming = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (incoming.isEmpty()) {
            diagnostics.record(SmsReceiverDiagnosticStatus.EMPTY_MESSAGE)
            return
        }

        val address = incoming.firstOrNull()?.displayOriginatingAddress.orEmpty()
        val body = incoming.joinToString(separator = "") { it.messageBody.orEmpty() }
        val timestamp = incoming.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        if (body.isBlank()) {
            diagnostics.record(SmsReceiverDiagnosticStatus.EMPTY_MESSAGE)
            return
        }

        val key = (address + "|" + timestamp + "|" + body).hashCode().toString()
        if (!markIfNew(context, key)) {
            diagnostics.record(SmsReceiverDiagnosticStatus.DUPLICATE)
            return
        }

        val blockSettings = SmsBlockRepository(context).getSettings()
        val isBlocked = SmsBlockFilter(
            blockSettings.blockedSenders,
            blockSettings.blockedContent
        ).isBlocked(address, body)
        if (isBlocked) {
            diagnostics.record(SmsReceiverDiagnosticStatus.BLOCKED)
            return
        }

        val rules = FilterRuleRepository(context).loadRules()
        val analysis = SmsClassifier(rules).analyze(address, body)
        val categoryId = analysis.categoryId
        val categoryLabel = rules.firstOrNull { it.categoryId == categoryId }?.displayName
            ?: SmsCategory.fromId(categoryId).label

        val settings = SmsNotificationSettingsRepository(context)
        if (!SmsNotificationPolicy.shouldNotify(false, settings.isEnabled(categoryId))) {
            diagnostics.record(
                SmsReceiverDiagnosticStatus.CATEGORY_NOTIFICATIONS_DISABLED,
                categoryId
            )
            return
        }

        val otpCode = analysis.otpCode.takeIf { categoryId == SmsCategory.OTP.id }
        if (otpCode != null && SmsOtpSettingsRepository(context).isAutoCopyEnabled()) {
            copyOtpToClipboard(context, otpCode)
        }

        val postResult = SmsNotificationManager.notifyIncoming(
            context = context,
            categoryId = categoryId,
            categoryLabel = categoryLabel,
            address = address,
            body = body,
            timestamp = timestamp,
            soundUri = settings.getSoundUri(categoryId),
            otpCode = otpCode
        )

        val diagnosticStatus = when (postResult) {
            SmsNotificationManager.PostResult.POSTED ->
                SmsReceiverDiagnosticStatus.NOTIFICATION_POSTED
            SmsNotificationManager.PostResult.POST_PERMISSION_DENIED ->
                SmsReceiverDiagnosticStatus.POST_PERMISSION_DENIED
            SmsNotificationManager.PostResult.APP_NOTIFICATIONS_DISABLED ->
                SmsReceiverDiagnosticStatus.APP_NOTIFICATIONS_DISABLED
            SmsNotificationManager.PostResult.CHANNEL_DISABLED ->
                SmsReceiverDiagnosticStatus.CHANNEL_DISABLED
            SmsNotificationManager.PostResult.POST_FAILED ->
                SmsReceiverDiagnosticStatus.NOTIFICATION_POST_FAILED
        }
        diagnostics.record(diagnosticStatus, categoryId)
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

    private fun markIfNew(context: Context, key: String): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = preferences.getStringSet(KEY_RECENT_KEYS, emptySet()).orEmpty().toMutableSet()
        if (!stored.add(key)) return false
        preferences.edit().putStringSet(
            KEY_RECENT_KEYS,
            stored.toList().takeLast(MAX_RECENT_KEYS).toSet()
        ).apply()
        return true
    }

    companion object {
        private const val TAG = "SmsReceiver"
        private const val PREFERENCES_NAME = "sms_receiver_state"
        private const val KEY_RECENT_KEYS = "recent_message_keys"
        private const val MAX_RECENT_KEYS = 64

        private val RECEIVER_EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor()
    }
}
