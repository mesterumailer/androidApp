package com.mesterumailer.smsmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.data.SmsBlockRepository
import com.mesterumailer.smsmanager.data.SmsNotificationSettingsRepository
import com.mesterumailer.smsmanager.model.SmsCategory
import com.mesterumailer.smsmanager.notification.SmsNotificationManager
import com.mesterumailer.smsmanager.util.SmsBlockFilter
import com.mesterumailer.smsmanager.util.SmsClassifier
import com.mesterumailer.smsmanager.util.SmsNotificationPolicy

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val incoming = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (incoming.isEmpty()) return

        val address = incoming.firstOrNull()?.displayOriginatingAddress.orEmpty()
        val body = incoming.joinToString(separator = "") { it.messageBody.orEmpty() }
        val timestamp = incoming.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        if (body.isBlank()) return

        val key = (address + "|" + timestamp + "|" + body).hashCode().toString()
        if (!markIfNew(context, key)) return

        val blockSettings = SmsBlockRepository(context).getSettings()
        val isBlocked = SmsBlockFilter(
            blockSettings.blockedSenders,
            blockSettings.blockedContent
        ).isBlocked(address, body)
        if (isBlocked) return

        val rules = FilterRuleRepository(context).loadRules()
        // Notification routing is intentionally independent from Inbox category activation.
        // A category may be hidden/disabled in the UI while still having its own notification enabled.
        val classifier = SmsClassifier(rules)
        val analysis = classifier.analyze(address, body)
        val categoryId = analysis.categoryId
        val categoryLabel = rules.firstOrNull { it.categoryId == categoryId }?.displayName
            ?: SmsCategory.fromId(categoryId).label

        val settings = SmsNotificationSettingsRepository(context)
        if (!SmsNotificationPolicy.shouldNotify(false, settings.isEnabled(categoryId))) return

        SmsNotificationManager.notifyIncoming(
            context = context,
            categoryId = categoryId,
            categoryLabel = categoryLabel,
            address = address,
            body = body,
            timestamp = timestamp,
            soundUri = settings.getSoundUri(categoryId)
        )
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
        private const val PREFERENCES_NAME = "sms_receiver_state"
        private const val KEY_RECENT_KEYS = "recent_message_keys"
        private const val MAX_RECENT_KEYS = 64
    }
}
