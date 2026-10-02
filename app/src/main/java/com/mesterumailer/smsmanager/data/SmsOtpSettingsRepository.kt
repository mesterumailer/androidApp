package com.mesterumailer.smsmanager.data

import android.content.Context

class SmsOtpSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isAutoCopyEnabled(): Boolean =
        preferences.getBoolean(KEY_AUTO_COPY_ENABLED, true)

    fun setAutoCopyEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_COPY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "sms_otp_settings"
        private const val KEY_AUTO_COPY_ENABLED = "auto_copy_enabled"
    }
}
