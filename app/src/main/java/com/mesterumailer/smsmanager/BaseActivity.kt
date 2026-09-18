package com.mesterumailer.smsmanager

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mesterumailer.smsmanager.data.AppTheme
import com.mesterumailer.smsmanager.data.ThemePreferenceRepository

abstract class BaseActivity : AppCompatActivity() {
    private var appliedTheme = AppTheme.LIGHT

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applySavedTheme(this)
        appliedTheme = ThemePreferenceRepository(this).getTheme()
        super.onCreate(savedInstanceState)
        AppThemeManager.configureWindow(this)
    }

    override fun onResume() {
        super.onResume()
        if (ThemePreferenceRepository(this).getTheme() != appliedTheme && !isFinishing) {
            recreate()
        }
    }

    protected fun rippleSurfaceBackground(color: Int, radiusDp: Int): android.graphics.drawable.RippleDrawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = (radiusDp * resources.displayMetrics.density).toFloat()
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = dp(radiusDp).toFloat()
        }
        return android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(getColor(R.color.touch_ripple)),
            content,
            mask
        )
    }


}