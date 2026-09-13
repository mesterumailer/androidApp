package com.mesterumailer.smsmanager

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.mesterumailer.smsmanager.data.CategoryVisibilityRepository
import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsCategory

class SettingsActivity : Activity() {
    private lateinit var repository: FilterRuleRepository
    private lateinit var visibilityRepository: CategoryVisibilityRepository
    private val editors = mutableMapOf<String, RuleEditors>()
    private val visibilityEditors = mutableMapOf<SmsCategory, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FilterRuleRepository(this)
        visibilityRepository = CategoryVisibilityRepository(this)
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "تنظیمات دسته‌بندی"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "از اینجا می‌توانید مشخص کنید کدام دسته‌بندی‌ها در Inbox نمایش داده شوند و قوانین تشخیص هر دسته را تنظیم کنید."
            textSize = 14f
            setPadding(0, dp(8), 0, dp(20))
        })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        content.addView(buildVisibilitySection())
        repository.loadRules().sortedBy { it.priority }.forEach { rule ->
            content.addView(buildRuleEditor(rule))
        }

        content.addView(Button(this).apply {
            text = "ذخیره همه تغییرات"
            setOnClickListener { saveAll() }
        })
        content.addView(Button(this).apply {
            text = "بازگردانی فیلترهای اولیه"
            setOnClickListener {
                repository.resetToDefaults()
                visibilityRepository.resetToDefaults()
                Toast.makeText(this@SettingsActivity, "تنظیمات اولیه بازگردانی شد.", Toast.LENGTH_SHORT).show()
                recreate()
            }
        })

        val scroll = ScrollView(this)
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun buildVisibilitySection(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(18))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFFF6F8FC.toInt())
            cornerRadius = dp(18).toFloat()
        }

        addView(TextView(this@SettingsActivity).apply {
            text = "نمایش دسته‌بندی‌ها"
            textSize = 20f
        })
        addView(TextView(this@SettingsActivity).apply {
            text = "دسته‌هایی که خاموش شوند از لیست Inbox مخفی می‌شوند، اما پیامک حذف نمی‌شود."
            textSize = 13f
            setPadding(0, dp(6), 0, dp(10))
        })

        SmsCategory.entries.forEach { category ->
            val checkBox = CheckBox(this@SettingsActivity).apply {
                text = category.label
                textSize = 15f
                isChecked = visibilityRepository.isVisible(category)
            }
            visibilityEditors[category] = checkBox
            addView(checkBox)
        }
    }

    private fun buildRuleEditor(rule: FilterRule): View {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), 0, dp(20))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        section.addView(TextView(this).apply {
            text = rule.displayName
            textSize = 20f
        })

        val editorsForRule = RuleEditors(
            sender = addEditor(section, "فرستنده شامل (اختیاری)", rule.senderContains),
            required = addEditor(section, "کلمات الزامی (همه باید وجود داشته باشند)", rule.requiredKeywords),
            any = addEditor(section, "کلمات تشخیصی (حداقل تعداد را پایین‌تر مشخص کنید)", rule.anyKeywords),
            excluded = addEditor(section, "کلمات ممنوع (با وجود هرکدام، دسته‌بندی رد می‌شود)", rule.excludedKeywords),
            minimum = addNumberEditor(section, "حداقل تعداد کلمات تشخیصی", rule.minimumAnyMatches),
            priority = addNumberEditor(section, "اولویت دسته (بیشتر = اولویت بالاتر در تساوی)", rule.priority)
        )
        editors[rule.categoryId] = editorsForRule
        return section
    }

    private fun addEditor(parent: LinearLayout, label: String, values: List<String>): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setPadding(0, dp(12), 0, dp(4))
        })
        return EditText(this).also { editor ->
            editor.setText(values.joinToString("\n"))
            editor.minLines = 2
            editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            parent.addView(editor, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun addNumberEditor(parent: LinearLayout, label: String, value: Int): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setPadding(0, dp(12), 0, dp(4))
        })
        return EditText(this).also { editor ->
            editor.setText(value.toString())
            editor.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            parent.addView(editor, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun saveAll() {
        val rules = repository.loadRules().map { rule ->
            val ui = editors[rule.categoryId] ?: return@map rule
            rule.copy(
                senderContains = parseLines(ui.sender.text.toString()),
                requiredKeywords = parseLines(ui.required.text.toString()),
                anyKeywords = parseLines(ui.any.text.toString()),
                excludedKeywords = parseLines(ui.excluded.text.toString()),
                minimumAnyMatches = ui.minimum.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 1,
                priority = ui.priority.text.toString().toIntOrNull() ?: 0
            )
        }
        repository.saveRules(rules)
        visibilityEditors.forEach { (category, checkBox) ->
            visibilityRepository.setVisible(category, checkBox.isChecked)
        }
        Toast.makeText(this, "تنظیمات ذخیره شد.", Toast.LENGTH_SHORT).show()
    }

    private fun parseLines(value: String): List<String> = value
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class RuleEditors(
        val sender: EditText,
        val required: EditText,
        val any: EditText,
        val excluded: EditText,
        val minimum: EditText,
        val priority: EditText
    )
}
