package com.mesterumailer.smsmanager

import androidx.appcompat.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.RingtoneManager
import android.graphics.Typeface
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.NotificationManagerCompat
import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.data.SmsNotificationSettingsRepository
import com.mesterumailer.smsmanager.notification.SmsNotificationManager
import com.mesterumailer.smsmanager.data.AppTheme
import com.mesterumailer.smsmanager.data.InboxReadMode
import com.mesterumailer.smsmanager.data.SmsBlockRepository
import com.mesterumailer.smsmanager.data.SmsSettingsRepository
import com.mesterumailer.smsmanager.data.ThemePreferenceRepository
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class AppSettingsActivity : BaseActivity() {
    private val notificationSettingsRepository by lazy { SmsNotificationSettingsRepository(this) }
    private var pendingSoundCategoryId: String? = null
    private var pendingSoundCategoryLabel: String? = null
    private var pendingNotificationCategoryId: String? = null
    private val backupExecutor = Executors.newSingleThreadExecutor()

    companion object {
        private const val SOUND_PICKER_REQUEST_CODE = 4101
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 4102
        private const val EXPORT_SETTINGS_REQUEST_CODE = 4103
        private const val IMPORT_SETTINGS_REQUEST_CODE = 4104
    }
    private val page: Int get() = getColor(R.color.page_background)
    private val card: Int get() = getColor(R.color.card_background)
    private val primary: Int get() = getColor(R.color.primary_text)
    private val secondary: Int get() = getColor(R.color.secondary_text)
    private val accent: Int get() = getColor(R.color.accent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    private fun buildSectionHeader(title: String, message: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, 0, 0, dp(8))
            addView(TextView(this@AppSettingsActivity).apply {
                text = title
                textSize = 18f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(TextView(this@AppSettingsActivity).apply {
                text = "?"
                textSize = 17f
                setTextColor(accent)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = rippleSurfaceBackground(getColor(R.color.accent_surface), 12)
                contentDescription = "راهنمای $title"
                isFocusable = true
                setOnClickListener {
                    AlertDialog.Builder(this@AppSettingsActivity)
                        .setTitle("راهنمای $title")
                        .setMessage(message)
                        .setPositiveButton("متوجه شدم", null)
                        .show()
                }
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
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

            addView(buildSectionHeader(
                "نمای برنامه",
                "در این بخش می‌توانید بین حالت روشن و تاریک جابه‌جا شوید. انتخاب شما به‌صورت محلی روی همین دستگاه ذخیره می‌شود و ظاهر بخش‌های برنامه را تغییر می‌دهد."
            ))

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

            addView(buildSectionHeader(
                "صندوق پیامک",
                "سقف پیامک تعداد پیام‌هایی را که برنامه از Inbox برای بررسی می‌خواند محدود می‌کند. در «آخرین پیام‌ها»، جدیدترین پیام‌ها تا سقف تعیین‌شده بررسی می‌شوند؛ در «تا تاریخ مشخص»، پیام‌های تا تاریخ انتخابی بررسی می‌شوند. ترتیب نمایش جدیدتر به قدیمی‌تر یا برعکس از دکمه مرتب‌سازی در صفحه اصلی تغییر می‌کند."
            ))

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
                text = "محدوده خواندن"
                textSize = 14f
                setTextColor(primary)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(8))
            })

            val rangeOptions = LinearLayout(this@AppSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            rangeOptions.addView(inboxRangeOption(InboxReadMode.LATEST_MESSAGES, readSettings.mode), LinearLayout.LayoutParams(0, dp(48), 1f).apply {
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
        body.addView(buildNotificationSettingsCard(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        body.addView(buildBackupCard(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(body, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }


    private fun buildBackupCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = roundedBackground(card, 22)
        elevation = dp(1).toFloat()

        addView(buildSectionHeader(
            "پشتیبان‌گیری و بازیابی",
            "از تنظیمات فعلی برنامه یک فایل JSON بسازید تا بتوانید آن را در محل دلخواه نگه دارید یا بعداً بازیابی کنید. فایل شامل تم، تنظیمات Inbox، فهرست مسدودی‌ها، دسته‌ها و قوانین تشخیص و تنظیمات اعلان است؛ خود پیامک‌ها و تغییر دسته‌بندی تک‌تک پیام‌ها در آن ذخیره نمی‌شوند. بازیابی، تنظیمات فعلی را با اطلاعات فایل جایگزین می‌کند. فایل در محل انتخابی شما ذخیره می‌شود و به اینترنت نیاز ندارد."
        ))

        val actions = LinearLayout(this@AppSettingsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            addView(backupActionButton("ذخیره فایل JSON") { openBackupFilePicker() },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
            addView(backupActionButton("بازیابی فایل") { openRestoreFilePicker() },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        }
        addView(actions)
    }

    private fun backupActionButton(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(accent)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = rippleSurfaceBackground(getColor(R.color.accent_surface), 14)
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun openBackupFilePicker() {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "sms-manager-settings-" + date + ".json")
        }
        runCatching { startActivityForResult(intent, EXPORT_SETTINGS_REQUEST_CODE) }
            .onFailure {
                Toast.makeText(this, "باز کردن محل ذخیره فایل ممکن نشد.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openRestoreFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        runCatching { startActivityForResult(intent, IMPORT_SETTINGS_REQUEST_CODE) }
            .onFailure {
                Toast.makeText(this, "باز کردن انتخاب‌گر فایل ممکن نشد.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun writeSettingsBackup(uri: Uri) {
        val appContext = applicationContext
        backupExecutor.execute {
            val result = runCatching {
                val json = com.mesterumailer.smsmanager.data.SettingsBackupManager.createJson(appContext)
                val output = appContext.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("امکان نوشتن فایل وجود ندارد.")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Toast.makeText(
                    this,
                    if (result.isSuccess) "فایل پشتیبان ذخیره شد." else "ذخیره فایل پشتیبان انجام نشد.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun readSettingsBackup(uri: Uri) {
        val appContext = applicationContext
        backupExecutor.execute {
            val result = runCatching {
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("امکان خواندن فایل وجود ندارد.")
                val output = ByteArrayOutputStream()
                input.use { stream ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > com.mesterumailer.smsmanager.data.SettingsBackupManager.MAX_FILE_BYTES) {
                            throw IllegalArgumentException("حجم فایل پشتیبان بیش از حد مجاز است.")
                        }
                        output.write(buffer, 0, count)
                    }
                }
                com.mesterumailer.smsmanager.data.SettingsBackupManager.parse(
                    String(output.toByteArray(), Charsets.UTF_8)
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { snapshot -> confirmSettingsRestore(snapshot) }
                    .onFailure {
                        Toast.makeText(
                            this,
                            it.message ?: "فایل پشتیبان معتبر نیست یا خوانده نشد.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    private fun confirmSettingsRestore(
        snapshot: com.mesterumailer.smsmanager.data.SettingsBackupSnapshot
    ) {
        AlertDialog.Builder(this)
            .setTitle("بازیابی تنظیمات")
            .setMessage(
                "تنظیمات فعلی با اطلاعات فایل جایگزین می‌شوند. " +
                    snapshot.rules.size + " قانون/دسته، " +
                    snapshot.blockedSenders.size + " فرستنده مسدود و " +
                    snapshot.blockedContent.size + " عبارت مسدود در فایل وجود دارد. " +
                    "پیامک‌های ذخیره‌شده در گوشی تغییر نمی‌کنند. ادامه می‌دهید؟"
            )
            .setNegativeButton("انصراف", null)
            .setPositiveButton("بازیابی") { _, _ -> applySettingsBackup(snapshot) }
            .show()
    }

    private fun applySettingsBackup(snapshot: com.mesterumailer.smsmanager.data.SettingsBackupSnapshot) {
        val appContext = applicationContext
        backupExecutor.execute {
            val result = runCatching {
                com.mesterumailer.smsmanager.data.SettingsBackupManager.restore(appContext, snapshot)
                val repository = SmsNotificationSettingsRepository(appContext)
                val labels = snapshot.rules.associate { it.categoryId to it.displayName }
                val ids = (snapshot.rules.map { it.categoryId } +
                    com.mesterumailer.smsmanager.model.SmsCategory.UNKNOWN.id).distinct()
                ids.forEach { id ->
                    runCatching {
                        SmsNotificationManager.recreateChannel(
                            appContext,
                            id,
                            labels[id] ?: com.mesterumailer.smsmanager.model.SmsCategory.fromId(id).label,
                            repository.getSoundUri(id)
                        )
                    }
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result.isSuccess) {
                    Toast.makeText(this, "تنظیمات بازیابی شد.", Toast.LENGTH_SHORT).show()
                    recreate()
                } else {
                    Toast.makeText(this, "بازیابی تنظیمات انجام نشد.", Toast.LENGTH_LONG).show()
                }
            }
        }
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

            addView(buildSectionHeader(
                "فیلتر مسدودسازی",
                "می‌توانید شماره یا بخشی از نام فرستنده و نیز عبارت‌هایی از متن پیام را به فهرست مسدودسازی اضافه کنید. پیام منطبق پیش از دسته‌بندی و اعلان در این برنامه کنار گذاشته می‌شود؛ پیامک اصلی در برنامه پیامک گوشی حذف یا تغییر نمی‌کند. برای برداشتن فیلتر، آن را از همین فهرست حذف کنید."
            ))

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
        background = rippleSurfaceBackground(getColor(R.color.accent_surface), 14)
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

    private fun buildNotificationSettingsCard(): View {
        val definitions = notificationCategoryDefinitions()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBackground(card, 22)
            elevation = dp(1).toFloat()

            addView(buildSectionHeader(
                "اعلان پیامک‌های جدید",
                "اعلان هر دسته مستقل از دسته‌های دیگر روشن یا خاموش می‌شود و برای هر دسته می‌توانید صدای جداگانه‌ای از صداهای گوشی انتخاب کنید. برنامه ویبرهٔ اعلان تنظیم نمی‌کند. نمایش اعلان به مجوز اعلان Android و تنظیمات اعلان خود گوشی نیز بستگی دارد."
            ))

            val permissionGranted = hasNotificationPermission()
            addView(TextView(this@AppSettingsActivity).apply {
                text = if (permissionGranted) "دسترسی اعلان Android: فعال" else "دسترسی اعلان Android: غیرفعال"
                textSize = 12f
                setTextColor(if (permissionGranted) accent else secondary)
                setPadding(0, 0, 0, dp(4))
            })
            definitions.forEachIndexed { index, (id, label) ->
                addView(buildNotificationCategoryRow(id, label, index))
            }
        }
    }

    private fun notificationCategoryDefinitions(): List<Pair<String, String>> {
        val rules = FilterRuleRepository(this).loadRules().sortedByDescending { it.priority }
        return (
            rules.map { it.categoryId to it.displayName } +
                (com.mesterumailer.smsmanager.model.SmsCategory.UNKNOWN.id to
                    com.mesterumailer.smsmanager.model.SmsCategory.UNKNOWN.label)
            ).distinctBy { it.first }
    }

    private fun buildNotificationCategoryRow(categoryId: String, label: String, index: Int): View {
        val enabled = notificationSettingsRepository.isEnabled(categoryId)
        val soundUri = notificationSettingsRepository.getSoundUri(categoryId)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rippleSurfaceBackground(getColor(R.color.soft_surface), 16)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = if (index == 0) 0 else dp(6)
            }

            addView(LinearLayout(this@AppSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL

                addView(LinearLayout(this@AppSettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(0, 0, dp(8), 0)
                    addView(TextView(this@AppSettingsActivity).apply {
                        text = label
                        textSize = 14f
                        setTextColor(primary)
                        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                    })
                    addView(TextView(this@AppSettingsActivity).apply {
                        text = if (enabled) "اعلان فعال" else "اعلان خاموش"
                        textSize = 11f
                        setTextColor(secondary)
                        setPadding(0, dp(3), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))

                addView(SwitchCompat(this@AppSettingsActivity).apply {
                    isChecked = enabled
                    contentDescription = "اعلان ${label}"
                    setOnCheckedChangeListener { button, checked ->
                        if (button.isPressed && checked && !hasNotificationPermission()) {
                            button.isChecked = false
                            pendingNotificationCategoryId = categoryId
                            requestNotificationPermission()
                            return@setOnCheckedChangeListener
                        }
                        notificationSettingsRepository.setEnabled(categoryId, checked)
                        if (checked) {
                            SmsNotificationManager.ensureChannel(
                                this@AppSettingsActivity,
                                categoryId,
                                label,
                                notificationSettingsRepository.getSoundUri(categoryId)
                            )
                        }
                    }
                }, LinearLayout.LayoutParams(dp(52), dp(48)))
            })

            addView(LinearLayout(this@AppSettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(0, dp(6), 0, 0)

                addView(TextView(this@AppSettingsActivity).apply {
                    text = "صدای اعلان"
                    textSize = 12f
                    setTextColor(secondary)
                    gravity = Gravity.CENTER_VERTICAL
                }, LinearLayout.LayoutParams(0, dp(40), 1f))

                addView(TextView(this@AppSettingsActivity).apply {
                    text = soundName(soundUri)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(accent)
                    background = rippleSurfaceBackground(getColor(R.color.accent_surface), 12)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    contentDescription = "انتخاب صدای اعلان برای ${label}"
                    setOnClickListener { showSoundPicker(categoryId, label) }
                }, LinearLayout.LayoutParams(-2, dp(40)))
            })
        }
    }

    private fun hasNotificationPermission(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        } else {
            openNotificationSettings()
        }
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            }
        )
    }

    private fun showSoundPicker(categoryId: String, label: String) {
        pendingSoundCategoryId = categoryId
        pendingSoundCategoryLabel = label
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "صدای اعلان «${label}»")
            notificationSettingsRepository.getSoundUri(categoryId)?.let {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
            }
        }
        startActivityForResult(intent, SOUND_PICKER_REQUEST_CODE)
    }

    private fun soundName(uri: Uri?): String {
        if (uri == null) return "بدون صدا"
        return runCatching {
            RingtoneManager.getRingtone(this, uri)?.getTitle(this)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "صدای انتخاب‌شده"
    }

    private fun readPickedSoundUri(data: Intent?): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data?.getParcelableExtra(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            SOUND_PICKER_REQUEST_CODE -> {
                val categoryId = pendingSoundCategoryId ?: return
                val label = pendingSoundCategoryLabel ?: categoryId
                val picked = readPickedSoundUri(data)
                notificationSettingsRepository.setSoundUri(categoryId, picked)
                SmsNotificationManager.recreateChannel(this, categoryId, label, picked)
                pendingSoundCategoryId = null
                pendingSoundCategoryLabel = null
                recreate()
            }
            EXPORT_SETTINGS_REQUEST_CODE -> data?.data?.let(::writeSettingsBackup)
            IMPORT_SETTINGS_REQUEST_CODE -> data?.data?.let(::readSettingsBackup)
        }
    }

    override fun onDestroy() {
        backupExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST_CODE) return

        val categoryId = pendingNotificationCategoryId
        pendingNotificationCategoryId = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && categoryId != null) {
            notificationSettingsRepository.setEnabled(categoryId, true)
            val label = notificationCategoryDefinitions().firstOrNull { it.first == categoryId }?.second
                ?: com.mesterumailer.smsmanager.model.SmsCategory.fromId(categoryId).label
            SmsNotificationManager.ensureChannel(
                this,
                categoryId,
                label,
                notificationSettingsRepository.getSoundUri(categoryId)
            )
            recreate()
        } else {
            Toast.makeText(this, "دسترسی اعلان‌ها داده نشد؛ اعلان دسته فعال نشد.", Toast.LENGTH_SHORT).show()
        }
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
