package com.mesterumailer.smsmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PermissionSetupActivity : BaseActivity() {
    private val smsPermissionRequestCode = 2001
    private var openedAppSettings = false
    private lateinit var statusView: TextView
    private lateinit var permissionButton: Button

    private val backgroundColor: Int get() = getColor(R.color.page_background)
    private val cardColor: Int get() = getColor(R.color.card_background)
    private val primaryText: Int get() = getColor(R.color.primary_text)
    private val secondaryText: Int get() = getColor(R.color.secondary_text)
    private val accent: Int get() = getColor(R.color.accent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) {
            if (hasRequiredSmsPermissions()) {
                openMain()
            } else if (openedAppSettings) {
                openedAppSettings = false
                statusView.text = "تنظیمات برنامه بررسی شد. حالا اجازه پیامک را فعال کنید."
                permissionButton.requestFocus()
            } else {
                refreshState()
            }
        }
    }

    private fun buildContent(): View {
        val root = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(28), dp(18), dp(28))
        }

        content.addView(TextView(this).apply {
            text = "یک مرحله تا شروع پیامک‌یار"
            textSize = 25f
            setTextColor(primaryText)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        content.addView(TextView(this).apply {
            text = "برای خواندن پیامک‌های دریافتی، ابتدا دسترسی‌های لازم را فعال کنید. پیامک‌های شما فقط داخل همین برنامه برای نمایش و دسته‌بندی خوانده می‌شوند."
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            setPadding(dp(12), dp(10), dp(12), dp(22))
        })

        content.addView(buildStepCard(
            number = "۱",
            title = "فعال‌سازی دسترسی محدودشده",
            description = "در بعضی گوشی‌ها، مخصوصاً برای برنامه‌هایی که خارج از فروشگاه نصب شده‌اند، ممکن است گزینه «Allow restricted settings / اجازه تنظیمات محدودشده» لازم باشد.",
            buttonText = "باز کردن اطلاعات برنامه",
            action = { openAppInfo() }
        ))

        content.addView(buildStepCard(
            number = "۲",
            title = "اجازه دسترسی به پیامک‌ها",
            description = "بعد از مرحله قبل، دکمه زیر را بزنید. این دسترسی برای خواندن Inbox و تشخیص پیامک جدید استفاده می‌شود؛ برنامه پیامک‌ها را حذف یا تغییر نمی‌دهد.",
            buttonText = "فعال‌سازی دسترسی SMS",
            action = { requestSmsPermission() }
        ))

        statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(18), dp(12), dp(8))
        }
        content.addView(statusView)

        content.addView(TextView(this).apply {
            text = "بعد از فعال شدن مجوز، برنامه به‌صورت خودکار وارد صندوق پیامک‌ها می‌شود."
            textSize = 12f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), 0)
        })

        root.addView(content, ViewGroup.LayoutParams(-1, -2))
        return root
    }

    private fun buildStepCard(
        number: String,
        title: String,
        description: String,
        buttonText: String,
        action: () -> Unit
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = roundedBackground(cardColor, 22)
        elevation = dp(2).toFloat()
        val badge = TextView(this@PermissionSetupActivity).apply {
            text = number
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = roundedBackground(accent, 15)
        }
        addView(badge, LinearLayout.LayoutParams(dp(38), dp(38)))

        addView(TextView(this@PermissionSetupActivity).apply {
            text = title
            textSize = 18f
            setTextColor(primaryText)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(5))
        })
        addView(TextView(this@PermissionSetupActivity).apply {
            text = description
            textSize = 13f
            setTextColor(secondaryText)
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, dp(14))
        })

        val button = Button(this@PermissionSetupActivity).apply {
            text = buttonText
            setOnClickListener { action() }
        }
        if (number == "۲") permissionButton = button
        addView(button, LinearLayout.LayoutParams(-1, dp(52)))
    }.apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        }
    }

    private fun refreshState() {
        if (hasReadSmsPermission()) {
            openMain()
        } else {
            statusView.text = "هنوز دسترسی خواندن SMS فعال نیست."
            permissionButton.isEnabled = true
        }
    }

    private fun requestSmsPermission() {
        if (hasReadSmsPermission()) {
            openMain()
            return
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            openMain()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS), smsPermissionRequestCode)
    }

    private fun openAppInfo() {
        openedAppSettings = true
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun hasReadSmsPermission(): Boolean =
        checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != smsPermissionRequestCode) return

        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            openMain()
            return
        }

        statusView.text = "دسترسی پیامک فعال نشد. اگر Android پیام «Restricted setting» یا «تنظیمات محدودشده» نشان می‌دهد، ابتدا مرحله ۱ را انجام دهید و سپس دوباره دسترسی SMS را امتحان کنید."
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun roundedBackground(color: Int, radiusDp: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
