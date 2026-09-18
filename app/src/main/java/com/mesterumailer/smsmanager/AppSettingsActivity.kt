package com.mesterumailer.smsmanager

import androidx.appcompat.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.graphics.Typeface
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mesterumailer.smsmanager.data.AppTheme
import com.mesterumailer.smsmanager.data.InboxReadMode
import com.mesterumailer.smsmanager.data.SmsBlockRepository
import com.mesterumailer.smsmanager.data.SmsSettingsRepository
import com.mesterumailer.smsmanager.data.ThemePreferenceRepository
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class AppSettingsActivity : BaseActivity() {
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(14), dp(16), dp(8))
        }
        toolbar.addView(ImageButton(this).apply {
            contentDescription = "بازگشت"
            setImageResource(android.R.drawable.ic_menu_revert)
            setColorFilter(primary)
            background = roundedBackground(Color.TRANSPARENT, 18)
            setPadding(dp(13), dp(13), dp(13), dp(13))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        toolbar.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(10), 0, dp(10), 0)
            addView(TextView(this@AppSettingsActivity).apply {
                text = "تنظیمات برنامه"
                textSize = 24f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(this@AppSettingsActivity).apply {
                text = "ظاهر و تنظیمات عمومی"
                textSize = 12f
                setTextColor(secondary)
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(toolbar)

        val settingsRepository = SmsSettingsRepository(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }

        val theme = ThemePreferenceRepository(this).getTheme()
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBackground(card, 22)
            elevation = dp(1).toFloat()

            addView(TextView(this@AppSettingsActivity).apply {
                text = "نمای برنامه"
                textSize = 18f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(this@AppSettingsActivity).apply {
                text = "حالت نمایش برنامه را انتخاب کنید. این انتخاب برای دفعات بعدی هم حفظ می‌شود."
                textSize = 13f
                setTextColor(secondary)
                setLineSpacing(0f, 1.12f)
                setPadding(0, dp(6), 0, dp(14))
            })

            val options = LinearLayout(this@AppSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            options.addView(themeOption(AppTheme.LIGHT, theme), LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(5)
            })
            options.addView(themeOption(AppTheme.DARK, theme), LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(5)
            })
            addView(options)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val readSettings = settingsRepository.getInboxReadSettings()
        val inboxCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBackground(getColor(R.color.accent_surface), 22)
            elevation = dp(1).toFloat()

            addView(TextView(this@AppSettingsActivity).apply {
                text = "صندوق پیامک"
                textSize = 18f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(this@AppSettingsActivity).apply {
                text = "تعداد پیامک‌های قابل خواندن و تاریخ مرجع را مشخص کنید."
                textSize = 13f
                setTextColor(secondary)
                setPadding(0, dp(6), 0, dp(14))
            })

            val limitRow = LinearLayout(this@AppSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                addView(LinearLayout(this@AppSettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    addView(TextView(this@AppSettingsActivity).apply {
                        text = "سقف پیامک"
                        textSize = 14f
                        setTextColor(primary)
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    })
                    addView(TextView(this@AppSettingsActivity).apply {
                        text = "بین ${SmsSettingsRepository.MIN_INBOX_LIMIT} تا ${SmsSettingsRepository.MAX_INBOX_LIMIT} پیامک"
                        textSize = 12f
                        setTextColor(secondary)
                        setPadding(0, dp(4), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(this@AppSettingsActivity).apply {
                    text = readSettings.limit.toString()
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(accent)
                    background = roundedBackground(card, 12)
                    setPadding(dp(14), dp(9), dp(14), dp(9))
                    setOnClickListener { showInboxLimitDialog() }
                    contentDescription = "تغییر سقف پیامک"
                })
            }
            addView(limitRow)

            addView(TextView(this@AppSettingsActivity).apply {
                text = "محدوده زمانی"
                textSize = 14f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(8))
            })

            val rangeOptions = LinearLayout(this@AppSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            rangeOptions.addView(inboxRangeOption(InboxReadMode.UNTIL_TODAY, readSettings.mode), LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(5)
            })
            rangeOptions.addView(inboxRangeOption(InboxReadMode.UNTIL_DATE, readSettings.mode), LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginEnd = dp(5)
            })
            addView(rangeOptions)

            addView(TextView(this@AppSettingsActivity).apply {
                text = readSettings.untilDateStartMillis?.let { formatDate(it) } ?: "انتخاب تاریخ"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(primary)
                background = rippleSurfaceBackground(getColor(R.color.soft_surface), 14)
                setPadding(dp(12), dp(11), dp(12), dp(11))
                visibility = if (readSettings.mode == InboxReadMode.UNTIL_DATE) View.VISIBLE else View.GONE
                setOnClickListener { showInboxDatePicker() }
                contentDescription = "انتخاب تاریخ مرجع پیامک"
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        }
        body.addView(inboxCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        body.addView(buildBlockSettingsCard(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        body.addView(TextView(this).apply {
            text = "در «تا امروز»، سقف پیامک‌ها از جدیدترین پیام‌ها محاسبه می‌شود. در «تا تاریخ مشخص»، پیام‌های بعد از تاریخ انتخاب‌شده وارد خواندن نمی‌شوند."
            textSize = 12f
            setTextColor(secondary)
            setLineSpacing(0f, 1.1f)
            setPadding(dp(4), dp(4), dp(4), 0)
        })
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }


    private fun buildBlockSettingsCard(): View {
        val repository = SmsBlockRepository(this)
        val settings = repository.getSettings()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBackground(card, 22)
            elevation = dp(1).toFloat()

            addView(TextView(this@AppSettingsActivity).apply {
                text = "فیلتر مسدودسازی"
                textSize = 18f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(this@AppSettingsActivity).apply {
                text = "پیام قبل از دسته‌بندی از این فیلتر عبور می‌کند. موارد مسدودشده در Inbox برنامه نمایش داده نمی‌شوند و SMS اصلی گوشی حذف نمی‌شود."
                textSize = 13f
                setTextColor(secondary)
                setLineSpacing(0f, 1.12f)
                setPadding(0, dp(6), 0, dp(14))
            })

            addView(blockSectionTitle("شماره‌ها و فرستنده‌های مسدود", settings.blockedSenders.size))
            settings.blockedSenders.forEach { value ->
                addView(blockRuleRow(value) {
                    repository.removeBlockedSender(value)
                    recreate()
                })
            }
            addBlockButton("افزودن شماره یا فرستنده") { showAddBlockDialog(true) }

            addView(blockSectionTitle("عبارت‌های محتوایی مسدود", settings.blockedContent.size).apply {
                setPadding(0, dp(18), 0, dp(8))
            })
            settings.blockedContent.forEach { value ->
                addView(blockRuleRow(value) {
                    repository.removeBlockedContent(value)
                    recreate()
                })
            }
            addBlockButton("افزودن عبارت محتوایی") { showAddBlockDialog(false) }

            if (settings.blockedSenders.isNotEmpty() || settings.blockedContent.isNotEmpty()) {
                addView(TextView(this@AppSettingsActivity).apply {
                    text = "حذف همه فیلترهای مسدودسازی"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(getColor(R.color.disabled_text))
                    background = rippleSurfaceBackground(getColor(R.color.reset_surface), 14)
                    setPadding(dp(10), dp(11), dp(10), dp(11))
                    setOnClickListener {
                        repository.clearAll()
                        recreate()
                    }
                }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
            }
        }
    }

    private fun blockSectionTitle(title: String, count: Int): TextView = TextView(this).apply {
        text = if (count == 0) title else "$title  •  $count"
        textSize = 13f
        setTextColor(primary)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(0, 0, 0, dp(8))
    }

    private fun blockRuleRow(value: String, onRemove: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = roundedBackground(getColor(R.color.soft_surface), 14)
        addView(TextView(this@AppSettingsActivity).apply {
            text = value
            textSize = 13f
            setTextColor(primary)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
        })
        addView(TextView(this@AppSettingsActivity).apply {
            text = "حذف"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.promotion_text))
            background = rippleSurfaceBackground(getColor(R.color.card_background), 12)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            contentDescription = "حذف فیلتر $value"
            setOnClickListener { onRemove() }
        }, LinearLayout.LayoutParams(-2, dp(38)).apply { marginStart = dp(6) })
    }.apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
    }

    private fun addBlockButton(label: String, action: () -> Unit): View = TextView(this).apply {
        text = "+  $label"
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(accent)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        background = roundedRippleBackground(getColor(R.color.accent_surface), 14)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        setOnClickListener { action() }
    }.apply {
        layoutParams = LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(2) }
    }

    private fun showAddBlockDialog(isSender: Boolean) {
        val editor = EditText(this).apply {
            hint = if (isSender) "مثلاً 09121234567 یا نام فرستنده" else "مثلاً تبلیغ، برنده شدید، کد تخفیف..."
            textSize = 14f
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(if (isSender) "افزودن شماره یا فرستنده" else "افزودن عبارت مسدود")
            .setMessage(if (isSender)
                "هر پیامکی که شماره یا نام فرستنده آن شامل این عبارت باشد، قبل از دسته‌بندی کنار گذاشته می‌شود."
                else
                "هر پیامکی که متن آن شامل این عبارت باشد، قبل از دسته‌بندی کنار گذاشته می‌شود.")
            .setView(editor)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("افزودن") { _, _ ->
                val value = editor.text.toString().trim()
                if (value.isBlank()) {
                    Toast.makeText(this, "مقدار را وارد کنید.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val repository = SmsBlockRepository(this)
                val added = if (isSender) repository.addBlockedSender(value) else repository.addBlockedContent(value)
                Toast.makeText(
                    this,
                    if (added) "فیلتر مسدودسازی اضافه شد." else "این فیلتر قبلاً وجود دارد.",
                    Toast.LENGTH_SHORT
                ).show()
                if (added) recreate()
            }
            .show()
    }

    private fun inboxRangeOption(mode: InboxReadMode, selected: InboxReadMode): TextView = TextView(this).apply {
        text = mode.label
        textSize = 13f
        gravity = Gravity.CENTER
        setTypeface(Typeface.DEFAULT, if (mode == selected) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (mode == selected) accent else primary)
        background = rippleSurfaceBackground(
            if (mode == selected) getColor(R.color.accent_surface) else getColor(R.color.soft_surface),
            14
        )
        setOnClickListener {
            val repository = SmsSettingsRepository(this@AppSettingsActivity)
            if (mode == InboxReadMode.UNTIL_DATE && repository.getInboxDateStartMillis() == null) {
                repository.setInboxDate(System.currentTimeMillis())
            }
            repository.setInboxReadMode(mode)
            if (mode == InboxReadMode.UNTIL_DATE) showInboxDatePicker() else recreate()
        }
    }

    private fun showInboxLimitDialog() {
        val current = SmsSettingsRepository(this).getInboxLimit()
        val editor = EditText(this).apply {
            setText(current.toString())
            selectAll()
            hint = "مثلاً 1000"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(4), dp(2), dp(4), 0)
            addView(TextView(this@AppSettingsActivity).apply {
                text = "پیش‌فرض: ${SmsSettingsRepository.DEFAULT_INBOX_LIMIT} پیامک\nحداقل: ${SmsSettingsRepository.MIN_INBOX_LIMIT} • حداکثر: ${SmsSettingsRepository.MAX_INBOX_LIMIT}"
                textSize = 12f
                setTextColor(secondary)
                setPadding(0, 0, 0, dp(8))
            })
            addView(editor)
        }
        AlertDialog.Builder(this)
            .setTitle("تعداد پیامک‌های Inbox")
            .setView(content)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره") { _, _ ->
                val requested = editor.text.toString().toIntOrNull()
                if (requested == null) {
                    Toast.makeText(this, "یک عدد معتبر وارد کنید.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                SmsSettingsRepository(this).setInboxLimit(requested)
                recreate()
            }
            .show()
    }

    private fun showInboxDatePicker() {
        val repository = SmsSettingsRepository(this)
        val current = repository.getInboxDateStartMillis()?.let {
            Calendar.getInstance().apply { timeInMillis = it }
        } ?: Calendar.getInstance()
        val today = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->
                repository.setInboxDate(year, month, day)
                repository.setInboxReadMode(InboxReadMode.UNTIL_DATE)
                recreate()
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = today.timeInMillis
        }.show()
    }

    private fun formatDate(startMillis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(startMillis))

    private fun themeOption(theme: AppTheme, selected: AppTheme): TextView = TextView(this).apply {
        text = theme.label
        textSize = 14f
        gravity = Gravity.CENTER
        setTypeface(Typeface.DEFAULT, if (theme == selected) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (theme == selected) accent else primary)
        background = rippleSurfaceBackground(
            if (theme == selected) getColor(R.color.accent_surface) else getColor(R.color.soft_surface),
            14
        )
        setOnClickListener {
            if (ThemePreferenceRepository(this@AppSettingsActivity).getTheme() == theme) return@setOnClickListener
            ThemePreferenceRepository(this@AppSettingsActivity).setTheme(theme)
            recreate()
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
