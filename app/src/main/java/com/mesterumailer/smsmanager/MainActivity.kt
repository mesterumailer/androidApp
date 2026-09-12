package com.mesterumailer.smsmanager

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.mesterumailer.smsmanager.data.SmsRepository
import com.mesterumailer.smsmanager.model.SmsCategory
import com.mesterumailer.smsmanager.model.SmsMessage
import java.util.Date

class MainActivity : Activity() {
    private val readSmsRequestCode = 1001
    private lateinit var repository: SmsRepository
    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SmsRepository(contentResolver)
        setContentView(buildContent())

        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS), readSmsRequestCode)
        } else {
            loadInbox()
        }
    }

    private fun buildContent(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            textDirection = android.view.View.TEXT_DIRECTION_RTL
        }

        val title = TextView(this).apply {
            text = "مدیریت پیامک‌ها"
            textSize = 26f
            setTextColor(Color.BLACK)
        }

        val description = TextView(this).apply {
            text = "فاز ۱ — فقط پیامک‌های دریافتی"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 16)
        }

        statusView = TextView(this).apply {
            text = "در حال بررسی دسترسی..."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 12)
        }

        val refreshButton = Button(this).apply {
            text = "به‌روزرسانی"
            setOnClickListener { loadInbox() }
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(listContainer, ViewGroup.LayoutParams(-1, -2))
        }

        root.addView(title, ViewGroup.LayoutParams(-1, -2))
        root.addView(description, ViewGroup.LayoutParams(-1, -2))
        root.addView(statusView, ViewGroup.LayoutParams(-1, -2))
        root.addView(refreshButton, ViewGroup.LayoutParams(-1, -2))
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 12 }
        )

        return root
    }

    private fun loadInbox() {
        if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            statusView.text = "برای خواندن Inbox باید اجازه دسترسی به پیامک‌ها را بدهید."
            return
        }

        try {
            val messages = repository.getInbox()
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

        messages.forEach { message ->
            listContainer.addView(createMessageView(message))
        }
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
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = 12
            }
            textDirection = android.view.View.TEXT_DIRECTION_RTL
        }
    }

    private fun backgroundFor(category: SmsCategory): Int = when (category) {
        SmsCategory.OTP -> 0xFFE8F5E9.toInt()
        SmsCategory.TRANSACTION -> 0xFFE3F2FD.toInt()
        SmsCategory.DELIVERY -> 0xFFFFF3E0.toInt()
        SmsCategory.SERVICE -> 0xFFF3E5F5.toInt()
        SmsCategory.PROMOTION -> 0xFFFFEBEE.toInt()
        SmsCategory.UNKNOWN -> 0xFFF5F5F5.toInt()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == readSmsRequestCode) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                loadInbox()
            } else {
                statusView.text = "دسترسی خواندن پیامک‌ها داده نشد. از تنظیمات برنامه می‌توانید آن را فعال کنید."
                Toast.makeText(this, "دسترسی READ_SMS لازم است.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
