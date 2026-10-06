package com.mesterumailer.smsmanager.data

import android.content.Context
import com.mesterumailer.smsmanager.model.SmsCategory

class MessageOverrideRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCategoryId(messageId: Long): String? =
        preferences.getString(messageId.toString(), null)

    fun setCategory(messageId: Long, categoryId: String) {
        preferences.edit().putString(messageId.toString(), categoryId).apply()
    }

    fun setCategory(messageId: Long, category: SmsCategory) {
        setCategory(messageId, category.id)
    }

    fun clearCategory(messageId: Long) {
        preferences.edit().remove(messageId.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "message_overrides"
    }
}
