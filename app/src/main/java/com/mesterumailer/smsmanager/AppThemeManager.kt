package com.mesterumailer.smsmanager

import android.app.Activity
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import com.mesterumailer.smsmanager.data.AppTheme
import com.mesterumailer.smsmanager.data.ThemePreferenceRepository

object AppThemeManager {
    fun applySavedTheme(activity: Activity) {
        val theme = ThemePreferenceRepository(activity).getTheme()
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
        activity.setTheme(
            when (theme) {
                AppTheme.LIGHT -> R.style.AppTheme
                AppTheme.DARK -> R.style.AppTheme_Dark
            }
        )
    }

    fun configureWindow(activity: Activity) {
        val dark = ThemePreferenceRepository(activity).getTheme() == AppTheme.DARK
        val background = activity.getColor(R.color.page_background)
        activity.window.statusBarColor = background
        activity.window.navigationBarColor = background
        activity.window.decorView.systemUiVisibility =
            if (dark) {
                0
            } else {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
    }
}
