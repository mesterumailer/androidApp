package com.mesterumailer.smsmanager.data

import android.content.Context
import com.mesterumailer.smsmanager.model.SmsCategory

class MessageOverrideRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCategory(messageId: Long): SmsCategory? =
        preferences.getString(messageId.toString(), null)?.let { value ->
            SmsCategory.entries.firstOrNull { it.name == value }
        }

    fun setCategory(messageId: Long, category: SmsCategory) {
        preferences.edit().putString(messageId.toString(), category.name).apply()
    }

    fun clearCategory(messageId: Long) {
        preferences.edit().remove(messageId.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "message_overrides"
    }
}
