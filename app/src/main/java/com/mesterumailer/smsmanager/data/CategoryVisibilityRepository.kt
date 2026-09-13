package com.mesterumailer.smsmanager.data

import android.content.Context
import com.mesterumailer.smsmanager.model.SmsCategory

class CategoryVisibilityRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isVisible(category: SmsCategory): Boolean =
        preferences.getBoolean(category.key(), true)

    fun setVisible(category: SmsCategory, visible: Boolean) {
        preferences.edit().putBoolean(category.key(), visible).apply()
    }

    fun resetToDefaults() {
        preferences.edit().clear().apply()
    }

    fun visibleCategories(): Set<SmsCategory> =
        SmsCategory.entries.filter(::isVisible).toSet()

    private fun SmsCategory.key(): String = "visible_${name}"

    companion object {
        private const val PREFS_NAME = "category_visibility"
    }
}
