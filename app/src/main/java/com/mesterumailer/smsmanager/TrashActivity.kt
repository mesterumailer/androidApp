package com.mesterumailer.smsmanager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.model.SmsCategory
import com.mesterumailer.smsmanager.data.SmsTrashEntry
import com.mesterumailer.smsmanager.data.SmsTrashRepository
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class TrashActivity : BaseActivity() {
    private val page: Int get() = getColor(R.color.page_background)
    private val card: Int get() = getColor(R.color.card_background)
    private val primary: Int get() = getColor(R.color.primary_text)
    private val secondary: Int get() = getColor(R.color.secondary_text)
    private val accent: Int get() = getColor(R.color.accent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val repository = SmsTrashRepository(this)
        val recent = repository.getRecent(SmsTrashRepository.DEFAULT_RECENT_LIMIT)
        val total = repository.getAll().size
        val labels = FilterRuleRepository(this).loadRules().associate { it.categoryId to it.displayName }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(page)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(14), dp(16), dp(8))
            addView(ImageButton(this@TrashActivity).apply {
                contentDescription = "بازگشت"
                setImageResource(android.R.drawable.ic_menu_revert)
                setColorFilter(primary)
                background = roundedBackground(Color.TRANSPARENT, 18)
                setPadding(dp(13), dp(13), dp(13), dp(13))
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(LinearLayout(this@TrashActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(10), 0, dp(10), 0)
                addView(TextView(this@TrashActivity).apply {
                    text = "سطل زباله"
                    textSize = 24f
                    setTextColor(primary)
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                })
                addView(TextView(this@TrashActivity).apply {
                    text = "مدیریت و بازیابی پیام‌های حذف‌شده"
                    textSize = 11f
                    setTextColor(secondary)
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(toolbar)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(8), dp(16), dp(24))
            addView(LinearLayout(this@TrashActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
                background = roundedBackground(card, 20)
                addView(TextView(this@TrashActivity).apply {
                    text = when {
                        total == 0 -> "سطل زباله خالی است."
                        total > SmsTrashRepository.DEFAULT_RECENT_LIMIT ->
                            total.toString() + " پیام حذف‌شده • نمایش " + SmsTrashRepository.DEFAULT_RECENT_LIMIT + " پیام اخیر"
                        else -> total.toString() + " پیام حذف‌شده"
                    }
                    textSize = 13f
                    setTextColor(secondary)
                    gravity = Gravity.CENTER
                })
                addView(TextView(this@TrashActivity).apply {
                    text = "پیام‌های حذف‌شده در پیامک‌یار باقی می‌مانند و SMS اصلی گوشی حذف نمی‌شود. از این صفحه می‌توانید هر پیام را دوباره به Inbox پیامک‌یار برگردانید."
                    textSize = 11f
                    setTextColor(secondary)
                    gravity = Gravity.CENTER
                    setLineSpacing(0f, 1.12f)
                    setPadding(0, dp(7), 0, 0)
                })
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

            if (recent.isEmpty()) {
                addView(LinearLayout(this@TrashActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(50), dp(20), dp(50))
                    background = roundedBackground(card, 20)
                    addView(TextView(this@TrashActivity).apply {
                        text = "پیامی برای بازیابی وجود ندارد."
                        textSize = 16f
                        setTextColor(primary)
                        gravity = Gravity.CENTER
                    })
                }, LinearLayout.LayoutParams(-1, -2))
            } else {
                recent.forEach { entry ->
                    addView(
                        buildTrashRow(
                            entry,
                            labels[entry.categoryId] ?: SmsCategory.fromId(entry.categoryId).label,
                            repository
                        ),
                        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                    )
                }
            }
        }

        root.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(body, ViewGroup.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun buildTrashRow(entry: SmsTrashEntry, categoryLabel: String, repository: SmsTrashRepository): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = roundedBackground(card, 18)
        elevation = dp(1).toFloat()
        addView(LinearLayout(this@TrashActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(TextView(this@TrashActivity).apply {
                text = entry.address.ifBlank { "فرستنده نامشخص" }
                textSize = 14f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(TextView(this@TrashActivity).apply {
                text = categoryLabel
                textSize = 10f
                setTextColor(accent)
                gravity = Gravity.CENTER
                background = roundedBackground(getColor(R.color.accent_surface), 12)
                setPadding(dp(8), dp(5), dp(8), dp(5))
            }, LinearLayout.LayoutParams(-2, dp(32)))
        })
        addView(TextView(this@TrashActivity).apply {
            text = formatDateTime(entry.timestamp)
            textSize = 11f
            setTextColor(secondary)
            setPadding(0, dp(5), 0, dp(5))
        })
        addView(TextView(this@TrashActivity).apply {
            text = entry.body
            textSize = 13f
            setTextColor(primary)
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.12f)
            setPadding(0, 0, 0, dp(8))
            setTextIsSelectable(true)
        })
        addView(TextView(this@TrashActivity).apply {
            text = "بازیابی پیام"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = rippleSurfaceBackground(getColor(R.color.accent_surface), 12)
            contentDescription = "بازیابی پیام از سطل زباله"
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setOnClickListener {
                if (repository.restore(entry.key)) {
                    Toast.makeText(this@TrashActivity, "پیام بازیابی شد.", Toast.LENGTH_SHORT).show()
                    recreate()
                } else {
                    Toast.makeText(this@TrashActivity, "بازیابی پیام انجام نشد.", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(42)))
    }

    private fun formatDateTime(timestamp: Long): String {
        val date = Date(timestamp)
        return DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(date) +
            "  •  " +
            DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(date)
    }

    private fun roundedBackground(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}