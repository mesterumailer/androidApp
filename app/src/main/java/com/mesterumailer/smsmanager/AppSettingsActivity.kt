package com.mesterumailer.smsmanager

import android.os.Bundle
import android.graphics.Typeface
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mesterumailer.smsmanager.data.AppTheme
import com.mesterumailer.smsmanager.data.ThemePreferenceRepository

class AppSettingsActivity : AppCompatActivity() {
    private val page: Int get() = getColor(R.color.page_background)
    private val card: Int get() = getColor(R.color.card_background)
    private val primary: Int get() = getColor(R.color.primary_text)
    private val secondary: Int get() = getColor(R.color.secondary_text)
    private val accent: Int get() = getColor(R.color.accent)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        AppThemeManager.configureWindow(this)
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

        body.addView(TextView(this).apply {
            text = "در حال حاضر فقط حالت روشن و تاریک قابل انتخاب است. تنظیمات دیگری به‌مرور در همین بخش اضافه می‌شوند."
            textSize = 12f
            setTextColor(secondary)
            setPadding(dp(4), dp(4), dp(4), 0)
        })
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun themeOption(theme: AppTheme, selected: AppTheme): TextView = TextView(this).apply {
        text = theme.label
        textSize = 14f
        gravity = Gravity.CENTER
        setTypeface(Typeface.DEFAULT, if (theme == selected) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (theme == selected) accent else primary)
        background = roundedBackground(
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
