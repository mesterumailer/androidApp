package com.mesterumailer.smsmanager

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.text.Spannable
import android.text.SpannableString
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import com.mesterumailer.smsmanager.data.CategoryActivationRepository
import com.mesterumailer.smsmanager.data.CategoryVisibilityRepository
import com.mesterumailer.smsmanager.data.SmsBlockRepository
import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.data.MessageOverrideRepository
import com.mesterumailer.smsmanager.data.SmsRepository
import com.mesterumailer.smsmanager.data.SmsSettingsRepository
import com.mesterumailer.smsmanager.data.InboxReadMode
import com.mesterumailer.smsmanager.data.InboxSortOrder
import com.mesterumailer.smsmanager.notification.SmsNotificationManager
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsCategory
import com.mesterumailer.smsmanager.model.SmsMessage
import com.mesterumailer.smsmanager.util.SmsInboxFilter
import com.mesterumailer.smsmanager.util.PerformanceLogger
import com.mesterumailer.smsmanager.util.SmsTextProcessor
import java.util.Date
import java.util.Calendar
import java.util.concurrent.Executors

class MainActivity : BaseActivity() {
    private val smsPermissionRequestCode = 1001
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var searchInput: EditText
    private lateinit var searchClear: TextView
    private lateinit var categoryFilterContainer: LinearLayout
    private val categoryFilterButtons = mutableMapOf<String, TextView>()
    private val selectedIds = linkedSetOf<Long>()
    private var currentMessages: List<SmsMessage> = emptyList()
    private var currentRules: List<FilterRule> = emptyList()
    private var inboxSortOrder: InboxSortOrder = InboxSortOrder.NEWEST_FIRST
    private lateinit var sortButton: TextView
    private var selectionBar: LinearLayout? = null
    private lateinit var processingOverlay: FrameLayout
    private lateinit var processingMessage: TextView
    private val processingHandler = Handler(Looper.getMainLooper())
    private val processingExecutor = Executors.newSingleThreadExecutor()
    private var processingToken = 0L
    private var processingShowTask: Runnable? = null

    private val pageBackground: Int get() = getColor(R.color.page_background)
    private val cardBackground: Int get() = getColor(R.color.card_background)
    private val primaryText: Int get() = getColor(R.color.primary_text)
    private val secondaryText: Int get() = getColor(R.color.secondary_text)
    private val accent: Int get() = getColor(R.color.accent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        handleNotificationIntent(intent)
        if (!hasRequiredSmsPermissions()) {
            requestPermissions(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS), smsPermissionRequestCode)
        } else {
            loadInbox()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::listContainer.isInitialized) return
        if (hasRequiredSmsPermissions()) loadInbox()
    }

    private fun buildContent(): DrawerLayout {
        drawerLayout = DrawerLayout(this).apply { setBackgroundColor(pageBackground) }
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackground)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        main.addView(buildToolbar(), LinearLayout.LayoutParams(-1, dp(62)))
        main.addView(buildSearchCard(), LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(10) })
        main.addView(buildCategoryFilterCard(), LinearLayout.LayoutParams(-1, dp(82)).apply { bottomMargin = dp(10) })
        main.addView(buildStatusCard(), LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(2) })
        selectionBar = buildSelectionBar()
        main.addView(selectionBar, LinearLayout.LayoutParams(-1, dp(60)).apply {
            topMargin = dp(10)
            bottomMargin = dp(2)
        })
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        main.addView(ScrollView(this).apply {
            setFillViewport(true)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(listContainer, ViewGroup.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        val contentFrame = FrameLayout(this).apply {
            addView(main, FrameLayout.LayoutParams(-1, -1))
            addView(buildProcessingOverlay(), FrameLayout.LayoutParams(-1, -1))
        }
        drawerLayout.addView(contentFrame, DrawerLayout.LayoutParams(-1, -1))
        drawerLayout.addView(buildDrawer(), DrawerLayout.LayoutParams(dp(326), -1).apply { gravity = Gravity.RIGHT })
        return drawerLayout
    }

    private fun buildProcessingOverlay(): FrameLayout = FrameLayout(this).apply {
        visibility = View.GONE
        setBackgroundColor(getColor(R.color.processing_overlay))
        isClickable = true
        isFocusable = true
        processingOverlay = this
        val card = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = roundedBackground(cardBackground, 18)
            elevation = dp(5).toFloat()
            addView(ProgressBar(this@MainActivity).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            })
            processingMessage = TextView(this@MainActivity).apply {
                text = "در حال پردازش..."
                textSize = 13f
                setTextColor(primaryText)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(processingMessage, LinearLayout.LayoutParams(-2, dp(32)).apply {
                marginEnd = dp(10)
            })
        }
        addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    }

    private fun beginProcessing(message: String = "در حال پردازش...") {
        processingToken += 1L
        processingShowTask?.let(processingHandler::removeCallbacks)
        val token = processingToken
        processingShowTask = Runnable {
            if (token != processingToken) return@Runnable
            processingMessage.text = message
            processingOverlay.visibility = View.VISIBLE
        }.also {
            processingHandler.postDelayed(it, 120L)
        }
    }

    private fun endProcessing() {
        processingToken += 1L
        processingShowTask?.let(processingHandler::removeCallbacks)
        processingShowTask = null
        if (::processingOverlay.isInitialized) processingOverlay.visibility = View.GONE
    }

    private fun requestRender(message: String = "در حال پردازش...") {
        if (!::listContainer.isInitialized) return
        beginProcessing(message)
        val token = processingToken
        val source = currentMessages
        val activeIds = activeCategoryDefinitions().map { it.first }
        val visibleIds = CategoryVisibilityRepository(this)
            .visibleCategoryIds(activeIds)
            .intersect(CategoryActivationRepository(this).activeCategoryIds(activeIds))
        val query = searchInput.text?.toString().orEmpty()

        processingExecutor.execute {
            val filterStart = android.os.SystemClock.elapsedRealtimeNanos()
            val filtered = SmsInboxFilter.filter(source, visibleIds, query)
            PerformanceLogger.logDuration("render_filter", filterStart)

            val sortStart = android.os.SystemClock.elapsedRealtimeNanos()
            val ordered = sortForDisplay(filtered)
            PerformanceLogger.logDuration("render_sort", sortStart)

            val linkifyStart = android.os.SystemClock.elapsedRealtimeNanos()
            val linkifiedBodies = ordered.associate { it.id to linkifyBody(it.body) }
            PerformanceLogger.logDuration("render_linkify", linkifyStart)

            runOnUiThread {
                if (token != processingToken || isFinishing) return@runOnUiThread
                renderFilteredMessages(ordered, source.size, query, token, linkifiedBodies)
            }
        }
    }

    private fun renderFilteredMessages(
        visibleMessages: List<SmsMessage>,
        sourceCount: Int,
        query: String,
        token: Long,
        linkifiedBodies: Map<Long, CharSequence> = emptyMap()
    ) {
        val renderStart = android.os.SystemClock.elapsedRealtimeNanos()
        var firstChunkLogged = false

        listContainer.removeAllViews()
        statusView.text = if (query.isBlank()) {
            visibleMessages.size.toString() + " پیامک"
        } else {
            visibleMessages.size.toString() + " نتیجه از " + sourceCount + " پیامک"
        }

        val visibleIds = visibleMessages.map { it.id }.toSet()
        selectedIds.retainAll(visibleIds)

        if (visibleMessages.isEmpty()) {
            listContainer.addView(buildEmptyState(query.isNotBlank()))
            updateSelectionBar(visibleMessages)
            endProcessing()
            return
        }

        var index = 0
        fun appendChunk() {
            if (token != processingToken || isFinishing) return
            val end = (index + 24).coerceAtMost(visibleMessages.size)
            val chunkStart = android.os.SystemClock.elapsedRealtimeNanos()
            for (i in index until end) {
                val message = visibleMessages[i]
                listContainer.addView(createMessageView(message, linkifiedBodies[message.id] ?: message.body))
            }
            PerformanceLogger.logDuration("render_chunk_$index-$end", chunkStart)

            index = end
            updateSelectionBar(visibleMessages)

            if (!firstChunkLogged) {
                firstChunkLogged = true
                PerformanceLogger.logDuration("render_first_chunk", renderStart)
            }

            if (index < visibleMessages.size) {
                listContainer.post { appendChunk() }
            } else {
                PerformanceLogger.logDuration("render_all_chunks", renderStart)
                endProcessing()
            }
        }
        appendChunk()
    }


    private fun buildToolbar(): View = FrameLayout(this).apply {
        background = roundedBackground(cardBackground, 22)
        elevation = dp(3).toFloat()
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(TextView(this@MainActivity).apply {
                text = "مدیریت پیامک‌ها"
                textSize = 20f
                setTextColor(primaryText)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            addView(TextView(this@MainActivity).apply {
                text = "صندوق پیامک"
                textSize = 11f
                setTextColor(secondaryText)
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
            layoutParams = FrameLayout.LayoutParams(-1, -1).apply {
                leftMargin = dp(66)
                rightMargin = dp(66)
            }
        })
        addView(ImageButton(this@MainActivity).apply {
            contentDescription = "باز کردن منوی برنامه"
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            imageTintList = ColorStateList.valueOf(primaryText)
            background = roundedBackground(Color.TRANSPARENT, 18)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { drawerLayout.openDrawer(Gravity.RIGHT) }
            layoutParams = FrameLayout.LayoutParams(dp(50), dp(50), Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(4) }
        })
    }

    private fun buildSearchCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(12), dp(5), dp(8), dp(5))
        background = roundedBackground(cardBackground, 20)
        addView(TextView(this@MainActivity).apply {
            text = "⌕"
            textSize = 24f
            setTextColor(accent)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(44))
        })
        searchInput = EditText(this@MainActivity).apply {
            hint = "جست‌وجو در متن یا نام فرستنده..."
            textSize = 14f
            setTextColor(primaryText)
            setHintTextColor(getColor(R.color.hint_text))
            background = null
            setSingleLine(true)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(dp(4), 0, dp(4), 0)
        }
        addView(searchInput, LinearLayout.LayoutParams(0, dp(44), 1f))
        searchClear = TextView(this@MainActivity).apply {
            text = "×"
            textSize = 24f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            visibility = View.GONE
            contentDescription = "پاک کردن جست‌وجو"
            setOnClickListener { searchInput.setText("") }
        }
        addView(searchClear, LinearLayout.LayoutParams(dp(36), dp(44)))
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchClear.visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE
                requestRender("در حال جست‌وجو...")
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun categoryDefinitions(): List<Pair<String, String>> {
        val rules = if (currentRules.isEmpty()) FilterRuleRepository(this).loadRules() else currentRules
        return rules.map { it.categoryId to it.displayName } + (SmsCategory.UNKNOWN.id to SmsCategory.UNKNOWN.label)
    }

    private fun activeCategoryDefinitions(): List<Pair<String, String>> {
        val activation = CategoryActivationRepository(this)
        return categoryDefinitions().filter { activation.isActive(it.first) }
    }

    private fun buildCategoryFilterCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(14), dp(7), dp(14), dp(7))
        background = roundedBackground(cardBackground, 20)
        addView(TextView(this@MainActivity).apply {
            text = "فیلتر نمایش"
            textSize = 15f
            setTextColor(primaryText)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        val horizontal = HorizontalScrollView(this@MainActivity).apply {
            isHorizontalScrollBarEnabled = false
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        categoryFilterContainer = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, 0)
        }
        horizontal.addView(categoryFilterContainer, ViewGroup.LayoutParams(-1, -2))
        addView(horizontal, LinearLayout.LayoutParams(-1, 0, 1f))
        createCategoryFilterButtons()
    }

    private fun createCategoryFilterButtons() {
        if (!::categoryFilterContainer.isInitialized) return
        categoryFilterContainer.removeAllViews()
        categoryFilterButtons.clear()
        addFilterButton("همه") {
            val repo = CategoryVisibilityRepository(this@MainActivity)
            activeCategoryDefinitions().forEach { (id, _) -> repo.setVisible(id, true) }
            refreshCategoryFilterButtons()
            requestRender("در حال اعمال فیلترها...")
        }
        activeCategoryDefinitions().forEach { (id, label) ->
            categoryFilterButtons[id] = addFilterButton(label) {
                val repo = CategoryVisibilityRepository(this@MainActivity)
                repo.setVisible(id, !repo.isVisible(id))
                refreshCategoryFilterButtons()
                requestRender("در حال اعمال فیلترها...")
            }
        }
        refreshCategoryFilterButtons()
    }

    private fun addFilterButton(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(dp(11), dp(6), dp(11), dp(6))
        setOnClickListener { action() }
        categoryFilterContainer.addView(this, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) })
    }

    private fun refreshCategoryFilterButtons() {
        if (!::categoryFilterContainer.isInitialized) return
        val ids = activeCategoryDefinitions().map { it.first }
        if (categoryFilterButtons.size != ids.size) {
            createCategoryFilterButtons()
            return
        }
        val repo = CategoryVisibilityRepository(this)
        categoryFilterButtons.forEach { (id, button) ->
            val visible = repo.isVisible(id)
            button.setTextColor(if (visible) accent else secondaryText)
            button.background = roundedRippleBackground(if (visible) getColor(R.color.accent_surface) else getColor(R.color.soft_surface), 15)
        }
        val allVisible = ids.isNotEmpty() && ids.all(repo::isVisible)
        (categoryFilterContainer.getChildAt(0) as? TextView)?.apply {
            setTextColor(if (allVisible) accent else secondaryText)
            background = roundedRippleBackground(if (allVisible) getColor(R.color.accent_surface) else getColor(R.color.soft_surface), 15)
        }
    }

    private fun buildStatusCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(12), dp(6), dp(8), dp(6))
        background = roundedBackground(cardBackground, 18)
        statusView = TextView(this@MainActivity).apply {
            text = "در حال آماده‌سازی صندوق پیامک‌ها..."
            textSize = 14f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        }
        addView(statusView)
        inboxSortOrder = SmsSettingsRepository(this@MainActivity).getInboxSortOrder()
        sortButton = TextView(this@MainActivity).apply {
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            contentDescription = "تغییر ترتیب نمایش پیام‌ها"
            setOnClickListener {
                inboxSortOrder = when (inboxSortOrder) {
                    InboxSortOrder.NEWEST_FIRST -> InboxSortOrder.OLDEST_FIRST
                    InboxSortOrder.OLDEST_FIRST -> InboxSortOrder.NEWEST_FIRST
                }
                SmsSettingsRepository(this@MainActivity).setInboxSortOrder(inboxSortOrder)
                refreshSortButton()
                requestRender("در حال تغییر ترتیب نمایش...")
            }
            layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(8) }
        }
        addView(sortButton)
        addView(TextView(this@MainActivity).apply {
            text = "↻"
            textSize = 24f
            setTextColor(accent)
            gravity = Gravity.CENTER
            background = roundedBackground(getColor(R.color.accent_surface), 16)
            contentDescription = "به‌روزرسانی پیامک‌ها"
            setOnClickListener { loadInbox() }
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginStart = dp(8) }
        })
        refreshSortButton()
    }

    private fun buildSelectionBar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        visibility = View.GONE
        setPadding(dp(8), dp(5), dp(8), dp(5))
        background = roundedBackground(getColor(R.color.selection_background), 18)
        addView(TextView(this@MainActivity).apply {
            textSize = 13f
            setTextColor(primaryText)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            tag = "selection_count"
        })
        addSelectionAction(this, "همه", "select_all") { toggleSelectAllVisible() }
        addSelectionAction(this, "کپی", "copy") { copySelectedMessages() }
        addSelectionAction(this, "اشتراک", "share") { shareSelectedMessages() }
        addSelectionAction(this, "لغو", "cancel") { clearSelection() }
    }

    private fun addSelectionAction(container: LinearLayout, label: String, tagValue: String, action: () -> Unit) {
        val view = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(accent)
            gravity = Gravity.CENTER
            background = roundedRippleBackground(cardBackground, 13)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            tag = tagValue
            setOnClickListener { action() }
        }
        container.addView(view, LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(5) })
    }

    private fun buildDrawer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(18), dp(30), dp(18), dp(20))
        setBackgroundColor(cardBackground)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = roundedBackground(getColor(R.color.accent_surface), 22)
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
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        addView(drawerSectionTitle("صندوق پیامک"))
        addView(drawerItem("▣", "پیامک‌های دریافتی", "مشاهده و دسته‌بندی Inbox", true, action = {
            drawerLayout.closeDrawer(Gravity.RIGHT)
        }))
        addView(drawerSectionTitle("تنظیمات"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        addView(drawerItem("⌘", "تنظیمات برنامه", "نمای برنامه و سایر تنظیمات", false, action = {
            startActivity(Intent(this@MainActivity, AppSettingsActivity::class.java))
            drawerLayout.closeDrawer(Gravity.RIGHT)
        }))
        addView(drawerItem("⚙", "مدیریت دسته‌بندی‌ها", "ساخت، ویرایش و حذف دسته‌ها", false, action = {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            drawerLayout.closeDrawer(Gravity.RIGHT)
        }))
    }

    private fun drawerSectionTitle(title: String): TextView = TextView(this).apply {
        text = title
        textSize = 12f
        setTextColor(secondaryText)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(dp(4), dp(2), dp(4), dp(8))
    }

    private fun drawerItem(icon: String, title: String, subtitle: String, selected: Boolean, action: (() -> Unit)?, enabled: Boolean = true): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = if (selected) roundedRippleBackground(getColor(R.color.accent_surface), 18) else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        alpha = if (enabled) 1f else 0.45f
        if (enabled && action != null) setOnClickListener { action() }
        addView(TextView(this@MainActivity).apply {
            text = icon
            textSize = 21f
            setTextColor(if (selected) accent else primaryText)
            gravity = Gravity.CENTER
            background = roundedBackground(if (selected) cardBackground else getColor(R.color.icon_surface), 14)
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
        if (!hasRequiredSmsPermissions()) {
            statusView.text = "برای خواندن Inbox باید اجازه دسترسی به پیامک‌ها را بدهید."
            return
        }

        beginProcessing("در حال خواندن پیامک‌ها...")
        val token = processingToken
        processingExecutor.execute {
            try {
                val loadStart = android.os.SystemClock.elapsedRealtimeNanos()
                val rules = FilterRuleRepository(this).loadRules()
                val readSettings = SmsSettingsRepository(this).getInboxReadSettings()
                val untilTimestampExclusive = if (readSettings.mode == InboxReadMode.UNTIL_DATE) {
                    readSettings.untilDateStartMillis?.let(::startOfNextDay)
                } else {
                    null
                }
                inboxSortOrder = readSettings.sortOrder
                val activeRuleIds = CategoryActivationRepository(this)
                    .activeCategoryIds(rules.map { it.categoryId })
                val classificationRules = rules.filter { it.categoryId in activeRuleIds }
                val blockSettings = SmsBlockRepository(this).getSettings()
                val blockFilter = com.mesterumailer.smsmanager.util.SmsBlockFilter(
                    blockedSenders = blockSettings.blockedSenders,
                    blockedContent = blockSettings.blockedContent
                )
                val messagesReadStart = android.os.SystemClock.elapsedRealtimeNanos()
                val rawMessages = SmsRepository(contentResolver, classificationRules, blockFilter).getInbox(
                    limit = readSettings.limit,
                    untilTimestampExclusive = untilTimestampExclusive
                )
                PerformanceLogger.logDuration("load_inbox_repository", messagesReadStart)

                val overridesStart = android.os.SystemClock.elapsedRealtimeNanos()
                val messages = applyMessageOverrides(rawMessages)
                PerformanceLogger.logDuration("load_inbox_overrides", overridesStart)
                PerformanceLogger.logDuration("load_inbox_total_background", loadStart)

                runOnUiThread {
                    if (token != processingToken || isFinishing) return@runOnUiThread
                    currentRules = rules
                    currentMessages = messages
                    refreshSortButton()
                    createCategoryFilterButtons()
                    showPendingNotificationIfReady()
                    requestRender("در حال نمایش پیام‌ها...")
                    statusView.contentDescription = buildReadWindowSummary(readSettings)
                }
            } catch (securityException: SecurityException) {
                runOnUiThread {
                    if (token != processingToken || isFinishing) return@runOnUiThread
                    endProcessing()
                    statusView.text = "دسترسی به پیامک‌ها رد شده است."
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    if (token != processingToken || isFinishing) return@runOnUiThread
                    endProcessing()
                    statusView.text = "خطا در خواندن پیامک‌ها: " + (exception.message ?: "خطای نامشخص")
                }
            }
        }
    }

    private fun refreshSortButton() {
        if (!::sortButton.isInitialized) return
        sortButton.text = when (inboxSortOrder) {
            InboxSortOrder.NEWEST_FIRST -> "جدید → قدیم"
            InboxSortOrder.OLDEST_FIRST -> "قدیم → جدید"
        }
        sortButton.setTextColor(if (inboxSortOrder == InboxSortOrder.NEWEST_FIRST) accent else secondaryText)
        sortButton.background = roundedRippleBackground(
            if (inboxSortOrder == InboxSortOrder.NEWEST_FIRST) getColor(R.color.accent_surface) else getColor(R.color.soft_surface),
            14
        )
    }

    private fun sortForDisplay(messages: List<SmsMessage>): List<SmsMessage> =
        when (inboxSortOrder) {
            InboxSortOrder.NEWEST_FIRST -> messages
            InboxSortOrder.OLDEST_FIRST -> messages.asReversed()
        }

    private fun buildReadWindowSummary(
        settings: com.mesterumailer.smsmanager.data.InboxReadSettings
    ): String = when (settings.mode) {
        com.mesterumailer.smsmanager.data.InboxReadMode.LATEST_MESSAGES ->
            "صندوق پیامک؛ ${settings.limit} پیامک اخیر"
        com.mesterumailer.smsmanager.data.InboxReadMode.UNTIL_DATE -> {
            val date = settings.untilDateStartMillis?.let { Date(it) }
            val formatted = date?.let { DateFormat.getDateFormat(this).format(it) } ?: "تاریخ مشخص"
            "صندوق پیامک؛ ${settings.limit} پیامک تا $formatted"
        }
    }

    private fun startOfNextDay(startMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = startMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

    private fun visibleMessages(): List<SmsMessage> {
        val ids = activeCategoryDefinitions().map { it.first }
        val visibleIds = CategoryVisibilityRepository(this)
            .visibleCategoryIds(ids)
            .intersect(CategoryActivationRepository(this).activeCategoryIds(ids))
        val query = searchInput.text?.toString().orEmpty()
        return SmsInboxFilter.filter(currentMessages, visibleIds, query)
    }

    private fun applyMessageOverrides(messages: List<SmsMessage>): List<SmsMessage> {
        val overrides = MessageOverrideRepository(this)
        return messages.map { message ->
            val overrideId = overrides.getCategoryId(message.id)
            if (overrideId.isNullOrBlank()) {
                message
            } else {
                message.copy(
                    analysis = message.analysis.copy(
                        category = SmsCategory.fromId(overrideId),
                        categoryId = overrideId
                    )
                )
            }
        }
    }

    private fun renderMessages(messages: List<SmsMessage>) {
        if (messages === currentMessages) {
            requestRender()
            return
        }
        beginProcessing()
        val token = processingToken
        val activeIds = activeCategoryDefinitions().map { it.first }
        val visibleIds = CategoryVisibilityRepository(this)
            .visibleCategoryIds(activeIds)
            .intersect(CategoryActivationRepository(this).activeCategoryIds(activeIds))
        val query = searchInput.text?.toString().orEmpty()
        processingExecutor.execute {
            val filtered = SmsInboxFilter.filter(messages, visibleIds, query)
            val ordered = sortForDisplay(filtered)
            val linkifiedBodies = ordered.associate { it.id to linkifyBody(it.body) }
            runOnUiThread {
                if (token != processingToken || isFinishing) return@runOnUiThread
                renderFilteredMessages(ordered, messages.size, query, token, linkifiedBodies)
            }
        }
    }

    private fun buildEmptyState(hasSearch: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(20), dp(36), dp(20), dp(36))
        background = roundedBackground(cardBackground, 20)
        addView(TextView(this@MainActivity).apply {
            text = if (hasSearch) "پیامی با این عبارت پیدا نشد." else "پیامی مطابق فیلترهای فعال پیدا نشد."
            textSize = 16f
            setTextColor(primaryText)
            gravity = Gravity.CENTER
        })
        addView(TextView(this@MainActivity).apply {
            text = if (hasSearch) "نام فرستنده یا بخش دیگری از متن پیام را امتحان کنید." else "از بخش «فیلتر نمایش» دسته‌های بیشتری را فعال کنید."
            textSize = 13f
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun createMessageView(message: SmsMessage, preparedBody: CharSequence = message.body): LinearLayout {
        val category = SmsCategory.fromId(message.analysis.categoryId)
        val label = currentRules.firstOrNull { it.categoryId == message.analysis.categoryId }?.displayName ?: category.label
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBackground(if (selectedIds.contains(message.id)) getColor(R.color.selected_message_background) else cardBackground, 20)
            elevation = dp(1).toFloat()
            val header = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            val select = CheckBox(this@MainActivity).apply {
                isChecked = selectedIds.contains(message.id)
                buttonTintList = ColorStateList.valueOf(accent)
                contentDescription = "انتخاب پیام"
                setOnClickListener { toggleSelection(message.id) }
            }
            header.addView(select, LinearLayout.LayoutParams(dp(42), dp(42)))
            header.addView(TextView(this@MainActivity).apply {
                text = message.address
                textSize = 14f
                setTextColor(primaryText)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            header.addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 11f
                setTextColor(categoryTextColor(category))
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = roundedBackground(categoryBackground(category), 14)
            })
            addView(header)
            addView(TextView(this@MainActivity).apply {
                text = "${DateFormat.getDateFormat(this@MainActivity).format(Date(message.timestamp))}  •  ${DateFormat.getTimeFormat(this@MainActivity).format(Date(message.timestamp))}"
                textSize = 11f
                setTextColor(secondaryText)
                setPadding(0, dp(5), 0, dp(8))
            })
            message.analysis.otpCode?.let { code ->
                addView(TextView(this@MainActivity).apply {
                    text = "کد تأیید  $code"
                    textSize = 14f
                    setTextColor(accent)
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    background = roundedBackground(getColor(R.color.accent_surface), 14)
                    contentDescription = "کد تأیید؛ برای کپی لمس کنید"
                    setOnClickListener { copyText(code) }
                })
            }
            message.analysis.amount?.let { amount ->
                addView(TextView(this@MainActivity).apply {
                    text = "مبلغ  $amount"
                    textSize = 13f
                    setTextColor(getColor(R.color.amount_text))
                    setPadding(0, dp(8), 0, 0)
                })
            }
            addView(TextView(this@MainActivity).apply {
                text = preparedBody
                textSize = 14f
                setTextColor(getColor(R.color.body_text))
                textDirection = View.TEXT_DIRECTION_RTL
                setLineSpacing(0f, 1.15f)
                setPadding(0, dp(10), 0, 0)
                setTextIsSelectable(true)
                linksClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = getColor(R.color.body_highlight)
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = "دسته‌بندی"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(accent)
                    setPadding(dp(11), dp(8), dp(11), dp(8))
                    background = roundedRippleBackground(getColor(R.color.accent_surface), 12)
                    contentDescription = "تغییر دسته‌بندی پیام"
                    setOnClickListener { showCategoryDialog(message) }
                }, LinearLayout.LayoutParams(-2, dp(40)))
                addView(TextView(this@MainActivity).apply {
                    text = "کپی متن"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(secondaryText)
                    setPadding(dp(11), dp(8), dp(11), dp(8))
                    background = roundedRippleBackground(getColor(R.color.icon_surface), 12)
                    setOnClickListener { copyText(message.body) }
                }, LinearLayout.LayoutParams(-2, dp(40)).apply { marginEnd = dp(8) })
            }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(8) })
        }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
    }

    private fun linkifyBody(body: String): CharSequence {
        if (!SmsTextProcessor.mayContainWebUrl(body)) return body
        val spannable = SpannableString(body)
        Linkify.addLinks(spannable, Linkify.WEB_URLS)
        return spannable
    }

    private fun showCategoryDialog(message: SmsMessage) {
        val definitions = activeCategoryDefinitions().distinctBy { it.first }

        if (definitions.isEmpty()) {
            Toast.makeText(this, "هیچ دسته‌ای برای انتخاب وجود ندارد.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentId = message.analysis.categoryId
        val checkedIndex = definitions.indexOfFirst { it.first == currentId }.coerceAtLeast(0)
        val labels = definitions.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("دسته‌بندی پیام")
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val selectedId = definitions[which].first
                MessageOverrideRepository(this).setCategory(message.id, selectedId)
                dialog.dismiss()
                beginProcessing("در حال اعمال دسته‌بندی...")
                val token = processingToken
                val snapshot = currentMessages
                processingExecutor.execute {
                    val updated = applyMessageOverrides(snapshot)
                    runOnUiThread {
                        if (token != processingToken || isFinishing) return@runOnUiThread
                        currentMessages = updated
                        requestRender("در حال نمایش پیام‌ها...")
                        Toast.makeText(this, "دسته «" + definitions[which].second + "» برای این پیام ذخیره شد.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNeutralButton("بازگشت به تشخیص خودکار") { _, _ ->
                MessageOverrideRepository(this).clearCategory(message.id)
                beginProcessing("در حال بازگردانی تشخیص خودکار...")
                val token = processingToken
                val snapshot = currentMessages
                processingExecutor.execute {
                    val updated = applyMessageOverrides(snapshot)
                    runOnUiThread {
                        if (token != processingToken || isFinishing) return@runOnUiThread
                        currentMessages = updated
                        requestRender("در حال نمایش پیام‌ها...")
                        Toast.makeText(this, "تشخیص خودکار پیام فعال شد.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }


    private fun toggleSelection(id: Long) {
        if (!selectedIds.add(id)) selectedIds.remove(id)
        requestRender("در حال به‌روزرسانی پیام‌ها...")
    }

    private fun toggleSelectAllVisible() {
        val visible = visibleMessages()
        if (visible.isEmpty()) return
        val ids = visible.map { it.id }.toSet()
        if (ids.all(selectedIds::contains)) selectedIds.removeAll(ids) else selectedIds.addAll(ids)
        requestRender("در حال به‌روزرسانی انتخاب‌ها...")
    }

    private fun clearSelection() {
        selectedIds.clear()
        requestRender("در حال به‌روزرسانی انتخاب‌ها...")
    }

    private fun updateSelectionBar(visibleMessages: List<SmsMessage> = visibleMessages()) {
        val bar = selectionBar ?: return
        bar.visibility = if (selectedIds.isEmpty()) View.GONE else View.VISIBLE
        bar.findViewWithTag<TextView>("selection_count")?.text = "${selectedIds.size} پیام"
        val visible = visibleMessages
        val allSelected = visible.isNotEmpty() && visible.all { selectedIds.contains(it.id) }
        bar.findViewWithTag<TextView>("select_all")?.apply {
            text = if (allSelected) "لغو همه" else "همه"
            setTextColor(if (allSelected) secondaryText else accent)
        }
    }

    private fun selectedMessages(): List<SmsMessage> = visibleMessages().filter { it.id in selectedIds }

    private fun exportText(): String = selectedMessages().mapIndexed { index, message ->
        val date = Date(message.timestamp)
        buildString {
            append("پیام ${index + 1}")
            append("\nفرستنده: ${message.address}")
            append("\nتاریخ: ${DateFormat.getDateFormat(this@MainActivity).format(date)}")
            append("\nساعت: ${DateFormat.getTimeFormat(this@MainActivity).format(date)}")
            append("\nدسته‌بندی: ${currentRules.firstOrNull { it.categoryId == message.analysis.categoryId }?.displayName ?: SmsCategory.fromId(message.analysis.categoryId).label}")
            append("\nمتن پیام:\n${message.body}")
        }
    }.joinToString("\n\n--------------------\n\n")

    private fun copySelectedMessages() {
        val messages = selectedMessages()
        if (messages.isEmpty()) {
            Toast.makeText(this, "پیامی انتخاب نشده است.", Toast.LENGTH_SHORT).show()
            return
        }
        copyText(exportText())
    }

    private fun shareSelectedMessages() {
        val messages = selectedMessages()
        if (messages.isEmpty()) {
            Toast.makeText(this, "پیامی برای اشتراک‌گذاری انتخاب نشده است.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, exportText())
        }, "اشتراک‌گذاری پیام‌ها"))
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("پیامک", text))
        Toast.makeText(this, "متن کپی شد.", Toast.LENGTH_SHORT).show()
    }

    private fun categoryBackground(category: SmsCategory): Int = when (category) {
        SmsCategory.TRANSACTION -> getColor(R.color.transaction_background)
        SmsCategory.PROMOTION -> getColor(R.color.promotion_background)
        SmsCategory.SERVICE -> getColor(R.color.service_background)
        SmsCategory.OTP -> getColor(R.color.otp_background)
        SmsCategory.DELIVERY -> getColor(R.color.delivery_background)
        SmsCategory.UNKNOWN -> getColor(R.color.unknown_background)
    }

    private fun categoryTextColor(category: SmsCategory): Int = when (category) {
        SmsCategory.TRANSACTION -> getColor(R.color.transaction_text)
        SmsCategory.PROMOTION -> getColor(R.color.promotion_text)
        SmsCategory.SERVICE -> getColor(R.color.service_text)
        SmsCategory.OTP -> accent
        SmsCategory.DELIVERY -> getColor(R.color.delivery_text)
        SmsCategory.UNKNOWN -> secondaryText
    }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun roundedRippleBackground(color: Int, radiusDp: Int): android.graphics.drawable.RippleDrawable {
        val content = roundedBackground(color, radiusDp)
        val mask = roundedBackground(Color.WHITE, radiusDp)
        return android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(getColor(R.color.touch_ripple)),
            content,
            mask
        )
    }

    override fun onDestroy() {
        processingShowTask?.let(processingHandler::removeCallbacks)
        processingExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun hasRequiredSmsPermissions(): Boolean =
        checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null || !intent.hasExtra(SmsNotificationManager.EXTRA_BODY)) return
        pendingNotificationAddress = intent.getStringExtra(SmsNotificationManager.EXTRA_ADDRESS).orEmpty()
        pendingNotificationBody = intent.getStringExtra(SmsNotificationManager.EXTRA_BODY).orEmpty()
        pendingNotificationTimestamp = intent.getLongExtra(SmsNotificationManager.EXTRA_TIMESTAMP, 0L)
        pendingNotificationCategoryLabel =
            intent.getStringExtra(SmsNotificationManager.EXTRA_CATEGORY_LABEL).orEmpty()
        if (::listContainer.isInitialized) showPendingNotificationIfReady()
    }

    private fun showPendingNotificationIfReady() {
        val body = pendingNotificationBody ?: return
        val address = pendingNotificationAddress.orEmpty()
        val timestamp = pendingNotificationTimestamp
        val categoryLabel = currentMessages
            .firstOrNull {
                it.address == address &&
                    it.body == body &&
                    kotlin.math.abs(it.timestamp - timestamp) <= 120_000L
            }
            ?.let { message ->
                currentRules.firstOrNull { it.categoryId == message.analysis.categoryId }?.displayName
                    ?: SmsCategory.fromId(message.analysis.categoryId).label
            }
            ?.takeIf { it.isNotBlank() }
            ?: pendingNotificationCategoryLabel?.takeIf { it.isNotBlank() }
            ?: SmsCategory.UNKNOWN.label

        pendingNotificationBody = null
        pendingNotificationAddress = null
        pendingNotificationTimestamp = 0L
        pendingNotificationCategoryLabel = null

        AlertDialog.Builder(this)
            .setTitle("پیامک جدید • $categoryLabel")
            .setMessage(buildString {
                if (address.isNotBlank()) append("فرستنده: $address\n\n")
                if (timestamp > 0L) {
                    append(DateFormat.getDateFormat(this@MainActivity).format(Date(timestamp)))
                    append("  •  ")
                    append(DateFormat.getTimeFormat(this@MainActivity).format(Date(timestamp)))
                    append("\n\n")
                }
                append(body)
            })
            .setPositiveButton("بستن", null)
            .show()
    }

    private var pendingNotificationAddress: String? = null
    private var pendingNotificationBody: String? = null
    private var pendingNotificationTimestamp: Long = 0L
    private var pendingNotificationCategoryLabel: String? = null

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != smsPermissionRequestCode) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadInbox()
        } else {
            statusView.text = "دسترسی‌های لازم پیامک فعال نشد."
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
