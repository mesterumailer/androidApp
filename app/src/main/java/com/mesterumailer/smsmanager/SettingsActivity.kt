package com.mesterumemailer.smsmanager

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.model.FilterRule

class SettingsActivity : Activity() {
    private lateinit var repository: FilterRuleRepository
    private lateinit var selector: Spinner
    private lateinit var summary: TextView
    private lateinit var editButton: Button
    private lateinit var deleteButton: Button
    private var rules: List<FilterRule> = emptyList()

    private val card = Color.WHITE
    private val page = Color.rgb(246, 248, 252)
    private val primary = Color.rgb(25, 31, 43)
    private val secondary = Color.rgb(103, 112, 129)
    private val accent = Color.rgb(52, 94, 255)

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
            setPadding(dp(20), dp(24), dp(20), dp(20))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "مدیریت دسته‌بندی‌ها"
            textSize = 26f
            setTextColor(primary)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "هر دسته را جداگانه انتخاب کنید؛ سپس قوانین تشخیص همان دسته را ویرایش، حذف یا دسته جدیدی ایجاد کنید."
            textSize = 14f
            setTextColor(secondary)
            setPadding(0, dp(8), 0, dp(16))
        })

        val content = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val categoryCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedBackground(card, 20)
        }
        categoryCard.addView(TextView(this).apply {
            text = "دسته‌بندی مورد نظر"
            textSize = 13f
            setTextColor(secondary)
        })
        selector = Spinner(this)
        categoryCard.addView(selector, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(5) })
        summary = TextView(this).apply {
            textSize = 12f
            setTextColor(secondary)
            setPadding(0, dp(8), 0, 0)
        }
        categoryCard.addView(summary)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(14), 0, 0)
        }
        editButton = Button(this).apply {
            text = "ویرایش قوانین"
            setOnClickListener { selectedRule()?.let(::showRuleEditor) }
        }
        deleteButton = Button(this).apply {
            text = "حذف دسته"
            setOnClickListener { selectedRule()?.let(::confirmDelete) }
        }
        actions.addView(editButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        actions.addView(deleteButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        categoryCard.addView(actions)
        body.addView(categoryCard)

        body.addView(Button(this).apply {
            text = "+ ساخت دسته جدید"
            setOnClickListener { showCreateDialog() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })

        body.addView(Button(this).apply {
            text = "بازگردانی دسته‌های اولیه"
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("بازگردانی دسته‌ها")
                    .setMessage("همه دسته‌های سفارشی حذف و دسته‌های اولیه جایگزین می‌شوند.")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("بازگردانی") { _, _ ->
                        repository.resetToDefaults()
                        refreshCategories()
                        Toast.makeText(this@SettingsActivity, "دسته‌های اولیه بازگردانی شدند.", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })

        content.addView(body, LinearLayout.LayoutParams(-1, -2))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun refreshCategories() {
        rules = repository.loadRules().sortedByDescending { it.priority }
        val labels = rules.map { it.displayName }.ifEmpty { listOf("هنوز دسته‌ای وجود ندارد") }
        selector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        selector.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSelectedSummary()
            }
        }
        updateSelectedSummary()
    }

    private fun selectedRule(): FilterRule? = rules.getOrNull(selector.selectedItemPosition)

    private fun updateSelectedSummary() {
        val rule = selectedRule()
        val active = rule != null
        editButton.isEnabled = active
        deleteButton.isEnabled = active
        summary.text = rule?.let {
            "${it.senderContains.size} فرستنده  •  ${it.requiredKeywords.size} کلمه الزامی  •  ${it.anyKeywords.size} کلمه تشخیصی  •  اولویت ${it.priority}"
        } ?: "دسته‌ای برای ویرایش وجود ندارد."
    }

    private fun showCreateDialog() {
        val name = EditText(this).apply {
            hint = "مثلاً بانک، قبض، سفر..."
            setSingleLine(true)
        }
        dialogWithFields("ساخت دسته جدید", name, null, isNew = true)
    }

    private fun showRuleEditor(rule: FilterRule) {
        val name = EditText(this).apply { setText(rule.displayName); setSingleLine(true) }
        dialogWithFields("ویرایش «${rule.displayName}»", name, rule, isNew = false)
    }

    private fun dialogWithFields(title: String, name: EditText, original: FilterRule?, isNew: Boolean) {
        val sender = multiEditor("فرستنده شامل", original?.senderContains ?: emptyList())
        val required = multiEditor("کلمات الزامی", original?.requiredKeywords ?: emptyList())
        val any = multiEditor("کلمات تشخیصی", original?.anyKeywords ?: emptyList())
        val excluded = multiEditor("کلمات ممنوع", original?.excludedKeywords ?: emptyList())
        val minimum = numberEditor("حداقل تعداد کلمات تشخیصی", original?.minimumAnyMatches ?: 1)
        val priority = numberEditor("اولویت", original?.priority ?: 10)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(label("نام دسته"))
            addView(name)
            addView(sender.first); addView(required.first); addView(any.first); addView(excluded.first)
            addView(minimum.first); addView(priority.first)
        }
        val scroll = ScrollView(this).apply { addView(content) }
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
                selector.setSelection(rules.indexOfFirst { it.categoryId == categoryId }.coerceAtLeast(0))
                Toast.makeText(this, "دسته ذخیره شد.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(rule: FilterRule) {
        AlertDialog.Builder(this)
            .setTitle("حذف «${rule.displayName}»")
            .setMessage("این دسته از قوانین تشخیص حذف می‌شود. پیامک‌ها حذف نمی‌شوند و پیام‌های بدون دسته مناسب در «سایر» قرار می‌گیرند.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("حذف") { _, _ ->
                repository.deleteRule(rule.categoryId)
                refreshCategories()
                Toast.makeText(this, "دسته حذف شد.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(secondary)
        setPadding(0, dp(9), 0, dp(4))
    }

    private fun multiEditor(title: String, values: List<String>): Pair<TextView, EditText> {
        val label = label("$title (هر مورد در یک خط)")
        val editor = EditText(this).apply {
            setText(values.joinToString("\n"))
            minLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        return label to editor
    }

    private fun numberEditor(title: String, value: Int): Pair<TextView, EditText> {
        val label = label(title)
        val editor = EditText(this).apply {
            setText(value.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        return label to editor
    }

    private fun parseLines(value: String): List<String> = value.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
