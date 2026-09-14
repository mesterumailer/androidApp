package com.mesterumailer.smsmanager.data

import android.content.Context
import com.mesterumailer.smsmanager.model.SmsCategory

class CategoryVisibilityRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isVisible(categoryId: String): Boolean =
        preferences.getBoolean(key(categoryId), true)

    fun setVisible(categoryId: String, visible: Boolean) {
        preferences.edit().putBoolean(key(categoryId), visible).apply()
    }

    fun resetToDefaults() {
        preferences.edit().clear().apply()
    }

    fun visibleCategoryIds(categoryIds: Collection<String>): Set<String> =
        categoryIds.filter(::isVisible).toSet()

    fun isVisible(category: SmsCategory): Boolean =
        isVisible(category.id)

    fun setVisible(category: SmsCategory, visible: Boolean) =
        setVisible(category.id, visible)

    fun visibleCategories(): Set<SmsCategory> =
        SmsCategory.entries.filter(::isVisible).toSet()

    private fun key(categoryId: String): String = "visible_$categoryId"

    companion object {
        private const val PREFS_NAME = "category_visibility"
    }
}
