package com.mesterumailer.smsmanager

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import com.mesterumailer.smsmanager.data.CategoryVisibilityRepository
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
    private val selectedIds = linkedSetOf<Long>()
    private var currentMessages: List<SmsMessage> = emptyList()
    private var selectionBar: LinearLayout? = null

    private val pageBackground = Color.rgb(246, 248, 252)
    private val cardBackground = Color.WHITE
    private val primaryText = Color.rgb(25, 31, 43)
    private val secondaryText = Color.rgb(103, 112, 129)
    private val accent = Color.rgb(52, 94, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageBackground
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
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
        drawerLayout.setBackgroundColor(pageBackground)

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackground)
            setPadding(dp(16), dp(18), dp(16), dp(12))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val toolbar = FrameLayout(this).apply {
            background = roundedBackground(cardBackground, 22)
            elevation = dp(3).toFloat()
            minimumHeight = dp(68)
        }
        toolbar.addView(TextView(this).apply {
            text = "مدیریت پیامک‌ها"
            textSize = 22f
            setTextColor(primaryText)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -1).apply {
                leftMargin = dp(66)
                rightMargin = dp(66)
            }
        })
        toolbar.addView(ImageButton(this).apply {
            contentDescription = "باز کردن منوی برنامه"
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            setColorFilter(primaryText)
            background = roundedBackground(Color.TRANSPARENT, 18)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener { drawerLayout.openDrawer(Gravity.RIGHT) }
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply {
                rightMargin = dp(6)
            }
        })
        main.addView(toolbar, LinearLayout.LayoutParams(-1, dp(68)))

        main.addView(TextView(this).apply {
            text = "فاز ۱  •  فقط پیامک‌های دریافتی"
            textSize = 13f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(12), 0, dp(8))
        })

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = roundedBackground(cardBackground, 18)
        }
        statusView = TextView(this).apply {
            text = "در حال آماده‌سازی صندوق پیامک‌ها..."
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        }
        statusCard.addView(statusView)
        val refreshButton = TextView(this).apply {
            text = "↻"
            textSize = 24f
            setTextColor(accent)
            gravity = Gravity.CENTER
            background = roundedBackground(Color.rgb(239, 243, 255), 16)
            setOnClickListener { loadInbox() }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) }
        }
        refreshButton.contentDescription = "به‌روزرسانی پیامک‌ها"
        statusCard.addView(refreshButton)
        main.addView(statusCard, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(4) })

        selectionBar = buildSelectionBar()
        main.addView(selectionBar, LinearLayout.LayoutParams(-1, dp(60)).apply {
            topMargin = dp(10)
            bottomMargin = dp(2)
        })

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val scrollView = ScrollView(this).apply {
            setFillViewport(true)
            isVerticalScrollBarEnabled = false
            addView(listContainer, ViewGroup.LayoutParams(-1, -2))
        }
        main.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        drawerLayout.addView(main, DrawerLayout.LayoutParams(-1, -1))
        drawerLayout.addView(buildDrawer(), DrawerLayout.LayoutParams(dp(326), -1).apply { gravity = Gravity.RIGHT })
        return drawerLayout
    }

    private fun buildSelectionBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        visibility = View.GONE
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = roundedBackground(Color.rgb(232, 237, 255), 18)

        addView(TextView(this@MainActivity).apply {
            textSize = 14f
            setTextColor(primaryText)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            tag = "selection_count"
        })
        addView(TextView(this@MainActivity).apply {
            text = "کپی"
            textSize = 13f
            setTextColor(accent)
            gravity = Gravity.CENTER
            background = roundedBackground(Color.WHITE, 14)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { copySelectedMessages() }
        })
        addView(TextView(this@MainActivity).apply {
            text = "لغو"
            textSize = 13f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { clearSelection() }
        })
    }

    private fun buildDrawer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(18), dp(30), dp(18), dp(20))
        setBackgroundColor(Color.WHITE)

        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = roundedBackground(Color.rgb(242, 245, 255), 22)
            addView(TextView(this@MainActivity).apply {
                text = "پیامک‌یار"
                textSize = 13f
                setTextColor(accent)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = "مرکز مدیریت و دسته‌بندی پیامک‌ها"
                textSize = 19f
                setTextColor(primaryText)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, dp(6), 0, dp(5))
            })
            addView(TextView(this@MainActivity).apply {
                text = "فاز ۱ • فقط پیامک‌های دریافتی"
                textSize = 12f
                setTextColor(secondaryText)
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        addView(drawerSectionTitle("صندوق پیامک"))
        addView(drawerItem(
            icon = "▣",
            title = "پیامک‌های دریافتی",
            subtitle = "مشاهده و دسته‌بندی Inbox",
            selected = true,
            action = { drawerLayout.closeDrawer(Gravity.RIGHT) }
        ))

        addView(drawerSectionTitle("تنظیمات"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        addView(drawerItem(
            icon = "⚙",
            title = "تنظیمات دسته‌بندی",
            subtitle = "قوانین تشخیص و نمایش دسته‌ها",
            selected = false,
            action = {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                drawerLayout.closeDrawer(Gravity.RIGHT)
            }
        ))
        addView(TextView(this@MainActivity).apply {
            text = "فازهای بعدی"
            textSize = 12f
            setTextColor(Color.rgb(160, 166, 178))
            setPadding(dp(4), dp(24), dp(4), dp(8))
        })
        addView(drawerItem("◎", "آمار و گزارش‌ها", "به‌زودی", false, null, enabled = false))
        addView(drawerItem("⌕", "تست قوانین", "به‌زودی", false, null, enabled = false))
        addView(TextView(this@MainActivity).apply {
            text = "نسخه ۰.۲"
            textSize = 11f
            setTextColor(Color.rgb(170, 175, 185))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
    }

    private fun drawerSectionTitle(title: String): TextView = TextView(this).apply {
        text = title
        textSize = 12f
        setTextColor(secondaryText)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(dp(4), dp(2), dp(4), dp(8))
    }

    private fun drawerItem(
        icon: String,
        title: String,
        subtitle: String,
        selected: Boolean,
        action: (() -> Unit)?,
        enabled: Boolean = true
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = roundedBackground(if (selected) Color.rgb(239, 243, 255) else Color.TRANSPARENT, 18)
        alpha = if (enabled) 1f else 0.45f
        if (enabled && action != null) setOnClickListener { action() }
        addView(TextView(this@MainActivity).apply {
            text = icon
            textSize = 21f
            setTextColor(if (selected) accent else primaryText)
            gravity = Gravity.CENTER
            background = roundedBackground(if (selected) Color.WHITE else Color.rgb(247, 248, 251), 14)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 15f
                setTextColor(primaryText)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                textSize = 11f
                setTextColor(secondaryText)
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -1, 1f))
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
        currentMessages = messages
        listContainer.removeAllViews()
        val visibleCategories = CategoryVisibilityRepository(this).visibleCategories()
        val visibleMessages = messages.filter { it.analysis.category in visibleCategories }
        statusView.text = "${visibleMessages.size} پیامک اخیر"
        if (visibleMessages.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "پیامکی مطابق دسته‌بندی‌های قابل نمایش پیدا نشد."
                textSize = 16f
                setTextColor(secondaryText)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
                background = roundedBackground(cardBackground, 20)
            })
            return
        }
        visibleMessages.forEach { listContainer.addView(createMessageView(it)) }
        updateSelectionBar()
    }

    private fun createMessageView(message: SmsMessage): LinearLayout {
        val category = message.analysis.category
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBackground(if (selectedIds.contains(message.id)) Color.rgb(238, 243, 255) else cardBackground, 20)
            elevation = dp(1).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val select = CheckBox(this).apply {
            isChecked = selectedIds.contains(message.id)
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnClickListener { toggleSelection(message.id) }
        }
        header.addView(select, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(TextView(this).apply {
            text = message.address
            textSize = 14f
            setTextColor(primaryText)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        header.addView(TextView(this).apply {
            text = category.label
            textSize = 11f
            setTextColor(categoryTextColor(category))
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundedBackground(categoryBackground(category), 14)
        })
        card.addView(header)
        val timeText = "${DateFormat.getDateFormat(this).format(Date(message.timestamp))}  •  ${DateFormat.getTimeFormat(this).format(Date(message.timestamp))}"
        card.addView(TextView(this).apply {
            text = timeText
            textSize = 11f
            setTextColor(secondaryText)
            setPadding(dp(0), dp(5), dp(0), dp(8))
        })
        message.analysis.otpCode?.let { code ->
            card.addView(TextView(this).apply {
                text = "کد تأیید  $code"
                textSize = 14f
                setTextColor(accent)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = roundedBackground(Color.rgb(239, 243, 255), 14)
                setOnClickListener { copyText(code) }
            })
        }
        message.analysis.amount?.let { amount ->
            card.addView(TextView(this).apply {
                text = "مبلغ  $amount"
                textSize = 13f
                setTextColor(Color.rgb(27, 116, 79))
                setPadding(0, dp(8), 0, 0)
            })
        }
        card.addView(TextView(this).apply {
            text = message.body
            textSize = 14f
            setTextColor(Color.rgb(64, 71, 84))
            textDirection = View.TEXT_DIRECTION_RTL
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(10), 0, 0)
            setOnLongClickListener {
                toggleSelection(message.id)
                true
            }
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(8), 0, 0)
        }
        actions.addView(TextView(this).apply {
            text = "ویرایش دسته"
            textSize = 12f
            setTextColor(accent)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = roundedBackground(Color.rgb(239, 243, 255), 12)
            setOnClickListener { showCategoryEditor(message) }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        actions.addView(TextView(this).apply {
            text = "کپی متن"
            textSize = 12f
            setTextColor(secondaryText)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = roundedBackground(Color.rgb(247, 248, 251), 12)
            setOnClickListener { copyText(message.body) }
        })
        card.addView(actions)
        return card.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) } }
    }

    private fun toggleSelection(id: Long) {
        if (!selectedIds.add(id)) selectedIds.remove(id)
        renderMessages(currentMessages)
    }

    private fun clearSelection() {
        selectedIds.clear()
        renderMessages(currentMessages)
    }

    private fun updateSelectionBar() {
        val bar = selectionBar ?: return
        bar.visibility = if (selectedIds.isEmpty()) View.GONE else View.VISIBLE
        val countView = bar.findViewWithTag<TextView>("selection_count") ?: return
        countView.text = "${selectedIds.size} پیام انتخاب شده"
    }

    private fun copySelectedMessages() {
        val text = currentMessages.filter { it.id in selectedIds }.joinToString("\n\n") { it.body }
        if (text.isNotBlank()) copyText(text) else Toast.makeText(this, "پیامی انتخاب نشده است.", Toast.LENGTH_SHORT).show()
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("پیامک", text))
        Toast.makeText(this, "متن کپی شد.", Toast.LENGTH_SHORT).show()
    }

    private fun showCategoryEditor(message: SmsMessage) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(6))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        container.addView(TextView(this).apply {
            text = "دسته‌بندی پیام"
            textSize = 20f
            setTextColor(primaryText)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        val categoryGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(12), 0, 0)
        }
        val options = SmsCategory.entries.toTypedArray()
        options.forEachIndexed { index, category ->
            val option = android.widget.RadioButton(this).apply {
                text = category.label
                isChecked = category == message.analysis.category
                setOnClickListener { tag = category }
                tag = category
            }
            categoryGroup.addView(option)
        }
        container.addView(categoryGroup)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(container)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره") { _, _ ->
                val selected = categoryGroup.childrenRecursive().firstOrNull { it is android.widget.RadioButton && it.isChecked } as? android.widget.RadioButton
                val category = selected?.tag as? SmsCategory ?: message.analysis.category
                saveManualCategory(message, category)
            }
            .create()
        dialog.show()
    }

    private fun saveManualCategory(message: SmsMessage, category: SmsCategory) {
        Toast.makeText(this, "دسته «${category.label}» برای این پیام ثبت شد.", Toast.LENGTH_SHORT).show()
    }

    private fun ViewGroup.childrenRecursive(): List<View> = buildList {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            add(child)
            if (child is ViewGroup) addAll(child.childrenRecursive())
        }
    }

    private fun categoryBackground(category: SmsCategory): Int = when (category) {
        SmsCategory.TRANSACTION -> Color.rgb(231, 247, 239)
        SmsCategory.PROMOTION -> Color.rgb(255, 239, 239)
        SmsCategory.SERVICE -> Color.rgb(241, 236, 255)
        SmsCategory.OTP -> Color.rgb(239, 243, 255)
        SmsCategory.DELIVERY -> Color.rgb(237, 244, 250)
        SmsCategory.UNKNOWN -> Color.rgb(242, 243, 246)
    }

    private fun categoryTextColor(category: SmsCategory): Int = when (category) {
        SmsCategory.TRANSACTION -> Color.rgb(27, 116, 79)
        SmsCategory.PROMOTION -> Color.rgb(181, 67, 67)
        SmsCategory.SERVICE -> Color.rgb(104, 73, 166)
        SmsCategory.OTP -> accent
        SmsCategory.DELIVERY -> Color.rgb(55, 91, 117)
        SmsCategory.UNKNOWN -> secondaryText
    }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
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
