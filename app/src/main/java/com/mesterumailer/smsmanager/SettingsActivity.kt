package com.mesterumailer.smsmanager

import androidx.appcompat.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.mesterumailer.smsmanager.data.CategoryVisibilityRepository
import com.mesterumailer.smsmanager.model.SmsCategory
import android.widget.Toast
import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.model.FilterRule

class SettingsActivity : BaseActivity() {
    private lateinit var repository: FilterRuleRepository
    private lateinit var categoryList: LinearLayout
    private lateinit var countView: TextView
    private var rules: List<FilterRule> = emptyList()

    private val page: Int get() = getColor(R.color.page_background)
    private val card: Int get() = getColor(R.color.card_background)
    private val primary: Int get() = getColor(R.color.primary_text)
    private val secondary: Int get() = getColor(R.color.secondary_text)
    private val accent: Int get() = getColor(R.color.accent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FilterRuleRepository(this)
        setContentView(buildContent())
        refreshCategories()
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
            addView(TextView(this@SettingsActivity).apply {
                text = "مدیریت دسته‌بندی‌ها"
                textSize = 22f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = "فعال‌سازی، غیرفعال‌سازی و ویرایش دسته‌ها"
                textSize = 12f
                setTextColor(secondary)
                setPadding(0, dp(3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(toolbar)
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(6), dp(16), dp(24))
        }

        val introCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBackground(getColor(R.color.accent_surface), 22)
            addView(TextView(this@SettingsActivity).apply {
                text = "قوانین تشخیص"
                textSize = 17f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = "برای هر دسته مشخص کنید در Inbox فعال باشد یا موقتاً از نمایش پیام‌ها خارج شود. غیرفعال‌کردن دسته، پیامک اصلی را حذف یا تغییر نمی‌دهد."
                textSize = 13f
                setTextColor(secondary)
                setPadding(0, dp(6), 0, 0)
            })
        }
        body.addView(introCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val sectionHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(TextView(this@SettingsActivity).apply {
                text = "دسته‌بندی‌ها"
                textSize = 16f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            countView = TextView(this@SettingsActivity).apply {
                textSize = 12f
                setTextColor(secondary)
            }
            addView(countView)
        }
        body.addView(sectionHeader, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        categoryList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        body.addView(categoryList)

        body.addView(TextView(this).apply {
            text = "+  ساخت دسته جدید"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = rippleSurfaceBackground(card, 18)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setOnClickListener { showCreateDialog() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(12)
            bottomMargin = dp(10)
        })

        body.addView(TextView(this).apply {
            text = "مدیریت پیشرفته"
            textSize = 13f
            setTextColor(secondary)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(4), dp(14), dp(4), dp(8))
        })

        body.addView(TextView(this).apply {
            text = "بازگردانی دسته‌های اولیه"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.disabled_text))
            background = rippleSurfaceBackground(getColor(R.color.reset_surface), 16)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setOnClickListener { confirmReset() }
        }, LinearLayout.LayoutParams(-1, dp(48)))

        scroll.addView(body, LinearLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun refreshCategories() {
        rules = repository.loadRules().sortedByDescending { it.priority }
        categoryList.removeAllViews()
        countView.text = buildEnabledCountLabel(rules)

        if (rules.isEmpty()) {
            categoryList.addView(emptyState())
            return
        }
        rules.forEachIndexed { index, rule ->
            categoryList.addView(createCategoryCard(rule, index))
        }
        categoryList.addView(createSystemCategoryCard(SmsCategory.UNKNOWN, rules.size))
    }

    private fun buildEnabledCountLabel(currentRules: List<FilterRule>): String {
        val visibility = CategoryVisibilityRepository(this)
        val total = currentRules.size + 1
        val enabled = currentRules.count { visibility.isVisible(it.categoryId) } +
            if (visibility.isVisible(SmsCategory.UNKNOWN.id)) 1 else 0
        return "$enabled از $total فعال"
    }

    private fun createCategoryCard(rule: FilterRule, index: Int): View {
        val visibility = CategoryVisibilityRepository(this)
        val enabled = visibility.isVisible(rule.categoryId)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = rippleSurfaceBackground(card, 20)
            elevation = dp(1).toFloat()

            addView(TextView(this@SettingsActivity).apply {
                text = ""
                background = roundedBackground(categoryAccent(index), 10)
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(44)).apply {
                    marginStart = dp(4)
                }
            })

            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                alpha = if (enabled) 1f else 0.58f
                setPadding(dp(12), 0, dp(8), 0)
                addView(TextView(this@SettingsActivity).apply {
                    text = rule.displayName
                    textSize = 15f
                    setTextColor(primary)
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                })
                addView(TextView(this@SettingsActivity).apply {
                    text = buildSummary(rule)
                    textSize = 11f
                    setTextColor(secondary)
                    setPadding(0, dp(4), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(8)
            })

            addView(SwitchCompat(this@SettingsActivity).apply {
                isChecked = enabled
                contentDescription = "فعال‌سازی ${rule.displayName}"
                setOnCheckedChangeListener { _, checked ->
                    visibility.setVisible(rule.categoryId, checked)
                    refreshCategories()
                    Toast.makeText(
                        this@SettingsActivity,
                        if (checked) "دسته «${rule.displayName}» فعال شد." else "دسته «${rule.displayName}» غیرفعال شد؛ پیام‌های این دسته در Inbox نمایش داده نمی‌شوند.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }, LinearLayout.LayoutParams(dp(52), dp(48)).apply {
                marginStart = dp(6)
            })

            addView(TextView(this@SettingsActivity).apply {
                text = "ویرایش"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(accent)
                background = rippleSurfaceBackground(getColor(R.color.accent_surface), 12)
                setPadding(dp(11), dp(8), dp(11), dp(8))
                contentDescription = "ویرایش ${rule.displayName}"
                setOnClickListener { showRuleEditor(rule) }
            }, LinearLayout.LayoutParams(-2, dp(38)))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun createSystemCategoryCard(category: SmsCategory, index: Int): View {
        val visibility = CategoryVisibilityRepository(this)
        val enabled = visibility.isVisible(category.id)

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = rippleSurfaceBackground(card, 20)
            elevation = dp(1).toFloat()

            addView(TextView(this@SettingsActivity).apply {
                text = ""
                background = roundedBackground(categoryAccent(index), 10)
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(44)).apply {
                    marginStart = dp(4)
                }
            })

            addView(LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                alpha = if (enabled) 1f else 0.58f
                setPadding(dp(12), 0, dp(8), 0)
                addView(TextView(this@SettingsActivity).apply {
                    text = category.label
                    textSize = 15f
                    setTextColor(primary)
                    setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                })
                addView(TextView(this@SettingsActivity).apply {
                    text = "دسته سیستمی • بدون قانون قابل ویرایش"
                    textSize = 11f
                    setTextColor(secondary)
                    setPadding(0, dp(4), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(8)
            })

            addView(SwitchCompat(this@SettingsActivity).apply {
                isChecked = enabled
                contentDescription = "فعال‌سازی ${category.label}"
                setOnCheckedChangeListener { _, checked ->
                    visibility.setVisible(category.id, checked)
                    refreshCategories()
                    Toast.makeText(
                        this@SettingsActivity,
                        if (checked) "دسته «${category.label}» فعال شد." else "دسته «${category.label}» غیرفعال شد؛ پیام‌های این دسته در Inbox نمایش داده نمی‌شوند.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }, LinearLayout.LayoutParams(dp(52), dp(48)))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            }
        }
    }
    private fun emptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(26), dp(24), dp(26))
        background = roundedBackground(card, 20)
        addView(TextView(this@SettingsActivity).apply {
            text = "هنوز دسته‌ای ساخته نشده است"
            textSize = 15f
            setTextColor(primary)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        addView(TextView(this@SettingsActivity).apply {
            text = "یک دسته جدید بسازید تا قوانین تشخیص آن را تعریف کنید."
            textSize = 12f
            setTextColor(secondary)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun buildSummary(rule: FilterRule): String {
        val parts = mutableListOf<String>()
        if (rule.senderContains.isNotEmpty()) parts += "${rule.senderContains.size} فرستنده"
        if (rule.requiredKeywords.isNotEmpty()) parts += "${rule.requiredKeywords.size} الزامی"
        if (rule.anyKeywords.isNotEmpty()) parts += "${rule.anyKeywords.size} تشخیصی"
        if (rule.excludedKeywords.isNotEmpty()) parts += "${rule.excludedKeywords.size} ممنوع"
        parts += "اولویت ${rule.priority}"
        return parts.joinToString("  •  ")
    }

    private fun showCreateDialog() {
        val name = singleLineEditor("نام دسته", "مثلاً بانک، سفر، قبض...")
        showRuleDialog("ساخت دسته جدید", name, null, true)
    }

    private fun showRuleEditor(rule: FilterRule) {
        val name = singleLineEditor("نام دسته", "نام دسته")
        name.setText(rule.displayName)
        showRuleDialog("ویرایش «${rule.displayName}»", name, rule, false)
    }

    private fun showRuleDialog(title: String, name: EditText, original: FilterRule?, isNew: Boolean) {
        val sender = multiEditor("فرستنده شامل", original?.senderContains ?: emptyList())
        val required = multiEditor("کلمات الزامی", original?.requiredKeywords ?: emptyList())
        val any = multiEditor("کلمات تشخیصی", original?.anyKeywords ?: emptyList())
        val excluded = multiEditor("کلمات ممنوع", original?.excludedKeywords ?: emptyList())
        val minimum = numberEditor("حداقل تعداد کلمات تشخیصی", original?.minimumAnyMatches ?: 1)
        val priority = numberEditor("اولویت دسته", original?.priority ?: 10)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(4), dp(2), dp(4), dp(4))
            addView(sectionTitle("اطلاعات دسته"))
            addView(name)
            addView(sectionTitle("قواعد تطبیق"))
            addView(sender.first); addView(sender.second)
            addView(required.first); addView(required.second)
            addView(any.first); addView(any.second)
            addView(excluded.first); addView(excluded.second)
            addView(sectionTitle("رفتار اولویت‌بندی"))
            addView(minimum.first); addView(minimum.second)
            addView(priority.first); addView(priority.second)
        }
        val scroll = ScrollView(this).apply {
            addView(content)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton("انصراف", null)
            .setPositiveButton("ذخیره", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val displayName = name.text.toString().trim()
                if (displayName.isBlank()) {
                    name.error = "نام دسته را وارد کنید"
                    return@setOnClickListener
                }
                val categoryId = original?.categoryId ?: "custom_${System.currentTimeMillis()}"
                val rule = FilterRule(
                    categoryId = categoryId,
                    displayName = displayName,
                    senderContains = parseLines(sender.second.text.toString()),
                    requiredKeywords = parseLines(required.second.text.toString()),
                    anyKeywords = parseLines(any.second.text.toString()),
                    excludedKeywords = parseLines(excluded.second.text.toString()),
                    minimumAnyMatches = minimum.second.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 1,
                    priority = priority.second.text.toString().toIntOrNull() ?: 10
                )
                val success = if (isNew) repository.addRule(rule) else repository.updateRule(rule)
                if (!success) {
                    Toast.makeText(this, "ذخیره دسته انجام نشد.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                refreshCategories()
                Toast.makeText(this, "دسته ذخیره شد.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("بازگردانی دسته‌ها")
            .setMessage("همه تغییرات دسته‌بندی و دسته‌های سفارشی حذف می‌شوند و دسته‌های اولیه برمی‌گردند.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("بازگردانی") { _, _ ->
                repository.resetToDefaults()
                refreshCategories()
                Toast.makeText(this, "دسته‌های اولیه بازگردانی شدند.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun singleLineEditor(title: String, hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun multiEditor(title: String, values: List<String>): Pair<TextView, EditText> {
        return label("$title (هر مورد در یک خط)") to EditText(this).apply {
            setText(values.joinToString("\n"))
            minLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
    }

    private fun numberEditor(title: String, value: Int): Pair<TextView, EditText> {
        return label(title) to EditText(this).apply {
            setText(value.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(primary)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(0, dp(12), 0, dp(6))
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(secondary)
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun parseLines(value: String): List<String> = value
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    private fun categoryAccent(index: Int): Int = when (index % 5) {
        0 -> Color.rgb(52, 94, 255)
        1 -> Color.rgb(37, 201, 138)
        2 -> Color.rgb(145, 93, 214)
        3 -> Color.rgb(232, 132, 53)
        else -> Color.rgb(68, 151, 190)
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
