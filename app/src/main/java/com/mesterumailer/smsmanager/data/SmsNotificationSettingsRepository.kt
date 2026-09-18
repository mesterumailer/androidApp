package com.mesterumailer.smsmanager.data

import android.content.Context
import android.net.Uri

class SmsNotificationSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isEnabled(categoryId: String): Boolean =
        preferences.getBoolean(enabledKey(categoryId), false)

    fun setEnabled(categoryId: String, enabled: Boolean) {
        preferences.edit().putBoolean(enabledKey(categoryId), enabled).apply()
    }

    fun getSoundUri(categoryId: String): Uri? =
        preferences.getString(soundKey(categoryId), null)?.let(Uri::parse)

    fun setSoundUri(categoryId: String, uri: Uri?) {
        val editor = preferences.edit()
        if (uri == null) editor.remove(soundKey(categoryId))
        else editor.putString(soundKey(categoryId), uri.toString())
        editor.apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "sms_notification_settings"
    }

    private fun enabledKey(categoryId: String): String = "enabled_$categoryId"
    private fun soundKey(categoryId: String): String = "sound_$categoryId"
}
