package com.mesterumailer.smsmanager.data

import android.content.Context
import android.net.Uri
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsCategory
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class NotificationBackupSetting(
    val enabled: Boolean,
    val soundUri: String?
)

data class SettingsBackupSnapshot(
    val theme: AppTheme,
    val inboxLimit: Int,
    val inboxReadMode: InboxReadMode,
    val inboxDateStartMillis: Long?,
    val inboxSortOrder: InboxSortOrder,
    val blockedSenders: List<String>,
    val blockedContent: List<String>,
    val rules: List<FilterRule>,
    val categoryActivation: Map<String, Boolean>,
    val categoryVisibility: Map<String, Boolean>,
    val notifications: Map<String, NotificationBackupSetting>
)

/**
 * Versioned JSON backup for app configuration only. SMS contents and per-message overrides
 * are intentionally excluded because they belong to the device's SMS storage.
 */
object SettingsBackupManager {
    private const val FORMAT = "mesterumailer.sms-manager.settings"
    private const val VERSION = 1
    const val MAX_FILE_BYTES = 2 * 1024 * 1024
    private const val MAX_LIST_ITEMS = 5000
    private const val MAX_TEXT_LENGTH = 1000

    fun createJson(context: Context): String {
        val inbox = SmsSettingsRepository(context).getInboxReadSettings()
        val theme = ThemePreferenceRepository(context).getTheme()
        val blocking = SmsBlockRepository(context).getSettings()
        val rules = FilterRuleRepository(context).loadRules()
        val categoryIds = (rules.map { it.categoryId } + SmsCategory.UNKNOWN.id).distinct()
        val activationRepository = CategoryActivationRepository(context)
        val visibilityRepository = CategoryVisibilityRepository(context)
        val notificationRepository = SmsNotificationSettingsRepository(context)

        val rulesArray = JSONArray()
        rules.forEach { rulesArray.put(it.toJson()) }

        val activation = JSONObject()
        val visibility = JSONObject()
        val notifications = JSONObject()
        categoryIds.forEach { id ->
            activation.put(id, activationRepository.isActive(id))
            visibility.put(id, visibilityRepository.isVisible(id))
            notifications.put(id, JSONObject().apply {
                put("enabled", notificationRepository.isEnabled(id))
                put("soundUri", notificationRepository.getSoundUri(id)?.toString() ?: JSONObject.NULL)
            })
        }

        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAtMillis", System.currentTimeMillis())
            put("appearance", JSONObject().put("theme", theme.id))
            put("inbox", JSONObject().apply {
                put("limit", inbox.limit)
                put("readMode", inbox.mode.id)
                put("dateStartMillis", inbox.untilDateStartMillis ?: JSONObject.NULL)
                put("sortOrder", inbox.sortOrder.id)
            })
            put("blocking", JSONObject().apply {
                put("senders", JSONArray(blocking.blockedSenders))
                put("content", JSONArray(blocking.blockedContent))
            })
            put("categories", JSONObject().apply {
                put("rules", rulesArray)
                put("activation", activation)
                put("visibility", visibility)
            })
            put("notifications", notifications)
        }.toString(2)
    }

    fun parse(json: String): SettingsBackupSnapshot {
        if (json.length > MAX_FILE_BYTES) invalid("حجم فایل پشتیبان بیش از حد مجاز است.")
        val root = runCatching { JSONObject(json) }
            .getOrElse { invalid("محتوای فایل JSON معتبر نیست.") }

        if (requiredString(root, "format") != FORMAT) {
            invalid("این فایل، پشتیبان تنظیمات پیامک‌یار نیست.")
        }
        if (requiredInt(root, "version") != VERSION) {
            invalid("نسخه این فایل پشتیبان پشتیبانی نمی‌شود.")
        }

        val appearance = requiredObject(root, "appearance")
        val themeId = requiredString(appearance, "theme")
        val theme = AppTheme.values().firstOrNull { it.id == themeId }
            ?: invalid("حالت ظاهری در فایل پشتیبان معتبر نیست.")

        val inbox = requiredObject(root, "inbox")
        val limit = requiredInt(inbox, "limit")
        if (limit !in SmsSettingsRepository.MIN_LIMIT..SmsSettingsRepository.MAX_LIMIT) {
            invalid("سقف پیامک در فایل پشتیبان خارج از محدوده مجاز است.")
        }
        val readModeId = requiredString(inbox, "readMode")
        val readMode = InboxReadMode.values().firstOrNull { it.id == readModeId }
            ?: invalid("محدوده خواندن Inbox در فایل پشتیبان معتبر نیست.")
        val sortOrderId = requiredString(inbox, "sortOrder")
        val sortOrder = InboxSortOrder.values().firstOrNull { it.id == sortOrderId }
            ?: invalid("ترتیب نمایش پیامک‌ها در فایل پشتیبان معتبر نیست.")
        val dateStartMillis = nullableLong(inbox, "dateStartMillis")
        if (readMode == InboxReadMode.UNTIL_DATE && dateStartMillis == null) {
            invalid("برای حالت «تا تاریخ مشخص»، تاریخ معتبری در فایل وجود ندارد.")
        }

        val blocking = requiredObject(root, "blocking")
        val blockedSenders = stringList(blocking, "senders")
        val blockedContent = stringList(blocking, "content")

        val categories = requiredObject(root, "categories")
        val rulesArray = categories.opt("rules") as? JSONArray
            ?: invalid("فهرست قوانین دسته‌بندی در فایل معتبر نیست.")
        if (rulesArray.length() > 500) invalid("تعداد دسته‌ها در فایل پشتیبان بیش از حد مجاز است.")
        val rules = buildList {
            for (index in 0 until rulesArray.length()) {
                val item = rulesArray.opt(index) as? JSONObject
                    ?: invalid("یکی از قوانین دسته‌بندی ساختار معتبری ندارد.")
                val id = requiredString(item, "categoryId").trim()
                val name = requiredString(item, "displayName").trim()
                if (!id.matches(Regex("[A-Za-z0-9_-]{1,100}")) || id == SmsCategory.UNKNOWN.id || name.isBlank() || name.length > 100) {
                    invalid("شناسه یا نام یکی از دسته‌های فایل معتبر نیست.")
                }
                val sender = stringList(item, "senderContains", 500, 500)
                val required = stringList(item, "requiredKeywords", 500, 500)
                val any = stringList(item, "anyKeywords", 500, 500)
                val excluded = stringList(item, "excludedKeywords", 500, 500)
                val minimum = requiredInt(item, "minimumAnyMatches")
                if (minimum !in 0..500) invalid("حداقل تطبیق یکی از قوانین معتبر نیست.")
                val priority = requiredInt(item, "priority")
                add(FilterRule(id, name, sender, required, any, excluded, minimum, priority))
            }
        }
        if (rules.map { it.categoryId }.distinct().size != rules.size) {
            invalid("شناسه دسته‌ها در فایل تکراری است.")
        }
        val categoryIds = (rules.map { it.categoryId } + SmsCategory.UNKNOWN.id).toSet()
        val activation = booleanMap(requiredObject(categories, "activation"), categoryIds)
        val visibility = booleanMap(requiredObject(categories, "visibility"), categoryIds)

        val notificationObject = requiredObject(root, "notifications")
        val notifications = linkedMapOf<String, NotificationBackupSetting>()
        val keys = notificationObject.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            if (id !in categoryIds) invalid("تنظیم اعلان برای یک دسته ناشناخته در فایل وجود دارد.")
            val item = requiredObject(notificationObject, id)
            val enabled = requiredBoolean(item, "enabled")
            if (!item.has("soundUri")) invalid("تنظیم صدای اعلان در فایل ناقص است.")
            val rawSound = item.opt("soundUri")
            val sound = if (rawSound === JSONObject.NULL) null else rawSound as? String
                ?: invalid("نشانی صدای اعلان در فایل معتبر نیست.")
            if (sound != null && sound.length > 2048) invalid("نشانی صدای اعلان بیش از حد طولانی است.")
            notifications[id] = NotificationBackupSetting(enabled, sound)
        }

        return SettingsBackupSnapshot(
            theme = theme,
            inboxLimit = limit,
            inboxReadMode = readMode,
            inboxDateStartMillis = dateStartMillis,
            inboxSortOrder = sortOrder,
            blockedSenders = blockedSenders,
            blockedContent = blockedContent,
            rules = rules,
            categoryActivation = activation,
            categoryVisibility = visibility,
            notifications = notifications
        )
    }

    fun restore(context: Context, snapshot: SettingsBackupSnapshot) {
        FilterRuleRepository(context).saveRules(snapshot.rules)

        ThemePreferenceRepository(context).setTheme(snapshot.theme)
        SmsSettingsRepository(context).apply {
            setInboxLimit(snapshot.inboxLimit)
            setInboxReadMode(snapshot.inboxReadMode)
            setInboxSortOrder(snapshot.inboxSortOrder)
            if (snapshot.inboxDateStartMillis == null) clearInboxDate()
            else setInboxDate(snapshot.inboxDateStartMillis)
        }
        SmsBlockRepository(context).replaceSettings(
            SmsBlockSettings(snapshot.blockedSenders, snapshot.blockedContent)
        )

        val categoryIds = (snapshot.rules.map { it.categoryId } + SmsCategory.UNKNOWN.id).distinct()
        CategoryActivationRepository(context).apply {
            resetToDefaults()
            categoryIds.forEach { id -> setActive(id, snapshot.categoryActivation[id] ?: true) }
        }
        CategoryVisibilityRepository(context).apply {
            resetToDefaults()
            categoryIds.forEach { id -> setVisible(id, snapshot.categoryVisibility[id] ?: true) }
        }
        SmsNotificationSettingsRepository(context).apply {
            clearAll()
            categoryIds.forEach { id ->
                val setting = snapshot.notifications[id]
                setEnabled(id, setting?.enabled ?: false)
                setSoundUri(id, setting?.soundUri?.let(Uri::parse))
            }
        }
    }

    private fun requiredObject(source: JSONObject, key: String): JSONObject =
        source.optJSONObject(key) ?: invalid("بخش " + key + " در فایل پشتیبان وجود ندارد یا معتبر نیست.")

    private fun requiredString(source: JSONObject, key: String): String =
        source.opt(key) as? String ?: invalid("مقدار متنی " + key + " در فایل پشتیبان معتبر نیست.")

    private fun requiredBoolean(source: JSONObject, key: String): Boolean =
        source.opt(key) as? Boolean ?: invalid("مقدار " + key + " در فایل پشتیبان باید روشن/خاموش باشد.")

    private fun requiredInt(source: JSONObject, key: String): Int {
        val value = source.opt(key) as? Number
            ?: invalid("مقدار عددی " + key + " در فایل پشتیبان معتبر نیست.")
        val longValue = value.toLong()
        if (!value.toDouble().isFinite() || value.toDouble() != longValue.toDouble() ||
            longValue < Int.MIN_VALUE || longValue > Int.MAX_VALUE
        ) invalid("مقدار عددی " + key + " در فایل پشتیبان معتبر نیست.")
        return longValue.toInt()
    }

    private fun nullableLong(source: JSONObject, key: String): Long? {
        if (!source.has(key)) invalid("مقدار " + key + " در فایل پشتیبان وجود ندارد.")
        val value = source.opt(key)
        if (value === JSONObject.NULL) return null
        val number = value as? Number ?: invalid("تاریخ فایل پشتیبان معتبر نیست.")
        val doubleValue = number.toDouble()
        if (!doubleValue.isFinite() || doubleValue != number.toLong().toDouble() || number.toLong() < 0L) {
            invalid("تاریخ فایل پشتیبان معتبر نیست.")
        }
        return number.toLong()
    }

    private fun stringList(
        source: JSONObject,
        key: String,
        maxItems: Int = MAX_LIST_ITEMS,
        maxLength: Int = MAX_TEXT_LENGTH
    ): List<String> {
        val array = source.opt(key) as? JSONArray
            ?: invalid("فهرست " + key + " در فایل پشتیبان معتبر نیست.")
        if (array.length() > maxItems) invalid("تعداد موارد " + key + " بیش از حد مجاز است.")
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.opt(index) as? String
                    ?: invalid("یکی از موارد " + key + " متنی نیست.")
                val normalized = value.trim()
                if (normalized.isBlank() || normalized.length > maxLength) {
                    invalid("یکی از موارد " + key + " خالی یا بیش از حد طولانی است.")
                }
                add(normalized)
            }
        }.distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun booleanMap(source: JSONObject, allowedIds: Set<String>): Map<String, Boolean> {
        val result = linkedMapOf<String, Boolean>()
        val keys = source.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            if (id !in allowedIds) invalid("وضعیت یک دسته ناشناخته در فایل پشتیبان وجود دارد.")
            result[id] = requiredBoolean(source, id)
        }
        return result
    }

    private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)
}
