package com.mesterumailer.smsmanager.data

import android.content.Context

class SmsSettingsRepository(context: Context) {
    companion object {
        const val DEFAULT_LIMIT = 1000
        const val MIN_LIMIT = 200
        const val MAX_LIMIT = 5000

        // Explicit names used by the UI/repository call sites.
        const val DEFAULT_INBOX_LIMIT = DEFAULT_LIMIT
        const val MIN_INBOX_LIMIT = MIN_LIMIT
        const val MAX_INBOX_LIMIT = MAX_LIMIT

        private const val PREFS = "sms_settings"
        private const val KEY_INBOX_LIMIT = "inbox_limit"
    }

    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getInboxLimit(): Int =
        preferences.getInt(KEY_INBOX_LIMIT, DEFAULT_LIMIT)
            .coerceIn(MIN_LIMIT, MAX_LIMIT)

    fun setInboxLimit(value: Int) {
        preferences.edit()
            .putInt(KEY_INBOX_LIMIT, value.coerceIn(MIN_LIMIT, MAX_LIMIT))
            .apply()
    }
}
