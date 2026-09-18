package com.mesterumailer.smsmanager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        AppThemeManager.configureWindow(this)
    }
}
