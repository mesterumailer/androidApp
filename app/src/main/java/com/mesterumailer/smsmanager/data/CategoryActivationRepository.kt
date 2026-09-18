package com.mesterumailer.smsmanager.data

import android.content.Context
import com.mesterumailer.smsmanager.model.SmsCategory

/**
 * Persists whether a category is available for classification/display.
 * This is separate from CategoryVisibilityRepository, which is the temporary Inbox filter.
 */
class CategoryActivationRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isActive(categoryId: String): Boolean =
        preferences.getBoolean(key(categoryId), true)

    fun setActive(categoryId: String, active: Boolean) {
        preferences.edit().putBoolean(key(categoryId), active).apply()
    }

    fun resetToDefaults() {
        preferences.edit().clear().apply()
    }

    fun activeCategoryIds(categoryIds: Collection<String>): Set<String> =
        categoryIds.filter(::isActive).toSet()

    fun isActive(category: SmsCategory): Boolean = isActive(category.id)

    fun setActive(category: SmsCategory, active: Boolean) =
        setActive(category.id, active)

    private fun key(categoryId: String): String = "active_$categoryId"

    companion object {
        private const val PREFS_NAME = "category_activation"
    }
}
