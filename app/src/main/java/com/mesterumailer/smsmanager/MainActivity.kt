package com.mesterumailer.smsmanager

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.data.SmsRepository
import com.mesterumailer.smsmanager.model.SmsCategory
import com.mesterumailer.smsmanager.model.SmsMessage
import java.util.Date

class MainActivity : Activity() {
    private val readSmsRequestCode = 1001
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), readSmsRequestCode)
        } else {
            loadInbox()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::listContainer.isInitialized && checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            loadInbox()
        }
    }

    private fun buildContent(): DrawerLayout {
        drawerLayout = DrawerLayout(this)

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(Button(this).apply {
            text = "☰"
            textSize = 22f
            contentDescription = "باز کردن منو"
            setOnClickListener { drawerLayout.openDrawer(Gravity.RIGHT) }
        }, LinearLayout.LayoutParams(64, 56))
        toolbar.addView(TextView(this).apply {
            text = "مدیریت پیامک‌ها"
            textSize = 24f
            setPadding(16, 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        main.addView(toolbar)

        main.addView(TextView(this).apply {
            text = "فاز ۱ — فقط پیامک‌های دریافتی"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 12)
        })

        statusView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 12)
        }
        main.addView(statusView)
        main.addView(Button(this).apply {
            text = "به‌روزرسانی پیامک‌ها"
            setOnClickListener { loadInbox() }
        })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        main.addView(ScrollView(this).apply {
            addView(listContainer, ViewGroup.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 12 })

        drawerLayout.addView(main, DrawerLayout.LayoutParams(-1, -1))
        drawerLayout.addView(buildDrawer(), DrawerLayout.LayoutParams(dp(320), -1).apply {
            gravity = Gravity.RIGHT
        })
        return drawerLayout
    }

    private fun buildDrawer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 56, 28, 28)
        setBackgroundColor(Color.WHITE)
        layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL

        addView(TextView(this@MainActivity).apply {
            text = "منوی برنامه"
            textSize = 25f
        })
        addView(TextView(this@MainActivity).apply {
            text = "پیامک‌های دریافتی"
            textSize = 17f
            setPadding(0, 36, 0, 20)
            setOnClickListener { drawerLayout.closeDrawer(Gravity.RIGHT) }
        })
        addView(Button(this@MainActivity).apply {
            text = "⚙ تنظیمات دسته‌بندی"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                drawerLayout.closeDrawer(Gravity.RIGHT)
            }
        })
    }

    private fun loadInbox() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            statusView.text = "برای خواندن Inbox باید اجازه دسترسی به پیامک‌ها را بدهید."
            return
        }
        try {
            val rules = FilterRuleRepository(this).loadRules()
            val messages = SmsRepository(contentResolver, rules).getInbox()
            renderMessages(messages)
        } catch (securityException: SecurityException) {
            statusView.text = "دسترسی به پیامک‌ها رد شده است."
        } catch (exception: Exception) {
            statusView.text = "خطا در خواندن پیامک‌ها: ${exception.message ?: "خطای نامشخص"}"
        }
    }

    private fun renderMessages(messages: List<SmsMessage>) {
        listContainer.removeAllViews()
        statusView.text = "${messages.size} پیامک اخیر"
        if (messages.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "پیامکی در Inbox پیدا نشد."
                textSize = 16f
                setPadding(0, 24, 0, 24)
            })
            return
        }
        messages.forEach { listContainer.addView(createMessageView(it)) }
    }

    private fun createMessageView(message: SmsMessage): TextView {
        val category = message.analysis.category
        val details = buildString {
            append("فرستنده: ${message.address}\n")
            append("زمان: ${DateFormat.getDateFormat(this@MainActivity).format(Date(message.timestamp))} ")
            append(DateFormat.getTimeFormat(this@MainActivity).format(Date(message.timestamp)))
            append("\nدسته: ${category.label}")
            message.analysis.otpCode?.let { append("\nکد: $it") }
            message.analysis.amount?.let { append("\nمبلغ: $it") }
            append("\n\n${message.body}")
        }
        return TextView(this).apply {
            text = details
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(16, 16, 16, 16)
            setBackgroundColor(backgroundFor(category))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
            textDirection = android.view.View.TEXT_DIRECTION_RTL
        }
    }

    private fun backgroundFor(category: SmsCategory): Int = when (category) {
        SmsCategory.TRANSACTION -> 0xFFE3F2FD.toInt()
        SmsCategory.PROMOTION -> 0xFFFFEBEE.toInt()
        SmsCategory.SERVICE -> 0xFFF3E5F5.toInt()
        SmsCategory.OTP, SmsCategory.DELIVERY, SmsCategory.UNKNOWN -> 0xFFF5F5F5.toInt()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == readSmsRequestCode) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) loadInbox()
            else statusView.text = "دسترسی خواندن پیامک‌ها داده نشد."
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
