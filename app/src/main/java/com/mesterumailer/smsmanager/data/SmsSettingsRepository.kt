package com.mesterumailer.smsmanager.data

import android.content.Context

class SmsSettingsRepository(context: Context) {
    companion object {
        const val DEFAULT_INBOX_LIMIT = 1000
        const val MIN_INBOX_LIMIT = 200
        const val MAX_INBOX_LIMIT = 5000
        private const val PREFS_NAME = "sms_settings"
        private const val KEY_INBOX_LIMIT = "inbox_limit"
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getInboxLimit(): Int = preferences.getInt(KEY_INBOX_LIMIT, DEFAULT_INBOX_LIMIT)
        .coerceIn(MIN_INBOX_LIMIT, MAX_INBOX_LIMIT)

    fun setInboxLimit(value: Int) {
        preferences.edit()
            .putInt(KEY_INBOX_LIMIT, value.coerceIn(MIN_INBOX_LIMIT, MAX_INBOX_LIMIT))
            .apply()
    }
}
