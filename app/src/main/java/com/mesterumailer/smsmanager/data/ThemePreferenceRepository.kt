package com.mesterumailer.smsmanager.data

import android.content.Context

enum class AppTheme(val id: String, val label: String) {
    LIGHT("light", "روشن"),
    DARK("dark", "تاریک");

    companion object {
        fun fromId(id: String?): AppTheme =
            entries.firstOrNull { it.id == id } ?: LIGHT
    }
}

class ThemePreferenceRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getTheme(): AppTheme =
        AppTheme.fromId(preferences.getString(KEY_THEME, AppTheme.LIGHT.id))

    fun setTheme(theme: AppTheme) {
        preferences.edit().putString(KEY_THEME, theme.id).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "sms_manager_theme_preferences"
        private const val KEY_THEME = "theme"
    }
}
