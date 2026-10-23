package com.mesterumailer.smsmanager.data

import android.content.Context

enum class SmsReceiverDiagnosticStatus(val label: String) {
    RECEIVER_STARTED("دریافت پیامک شروع شد؛ در حال بررسی"),
    EMPTY_MESSAGE("متن پیامک خالی بود و نادیده گرفته شد"),
    DUPLICATE("پیامک تکراری تشخیص داده شد"),
    BLOCKED("پیامک با فیلتر مسدودسازی تطبیق داشت"),
    CATEGORY_NOTIFICATIONS_DISABLED("اعلان این دسته داخل پیامک‌یار خاموش است"),
    POST_PERMISSION_DENIED("مجوز اعلان Android داده نشده است"),
    APP_NOTIFICATIONS_DISABLED("اعلان‌های کلی برنامه در Android خاموش هستند"),
    CHANNEL_DISABLED("کانال اعلان این دسته در تنظیمات Android خاموش است"),
    NOTIFICATION_POSTED("اعلان به Android تحویل داده شد"),
    NOTIFICATION_POST_FAILED("Android اعلان را نپذیرفت"),
    PROCESSING_ERROR("هنگام پردازش پیامک در پس‌زمینه خطا رخ داد");

    companion object {
        fun fromId(id: String?): SmsReceiverDiagnosticStatus? =
            entries.firstOrNull { it.name == id }
    }
}

data class SmsReceiverDiagnosticResult(
    val status: SmsReceiverDiagnosticStatus,
    val categoryId: String?,
    val timestampMillis: Long,
    val detailCode: String?
)

/**
 * Stores only the latest receiver outcome (status, category, time, and optional error class).
 * SMS body and sender are deliberately never persisted for diagnostics.
 */
class SmsReceiverDiagnosticsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun record(
        status: SmsReceiverDiagnosticStatus,
        categoryId: String? = null,
        detailCode: String? = null
    ) {
        preferences.edit()
            .putString(KEY_STATUS, status.name)
            .putString(KEY_CATEGORY, categoryId)
            .putString(KEY_DETAIL, detailCode)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getLastResult(): SmsReceiverDiagnosticResult? {
        val status = SmsReceiverDiagnosticStatus.fromId(preferences.getString(KEY_STATUS, null))
            ?: return null
        return SmsReceiverDiagnosticResult(
            status = status,
            categoryId = preferences.getString(KEY_CATEGORY, null),
            timestampMillis = preferences.getLong(KEY_TIMESTAMP, 0L),
            detailCode = preferences.getString(KEY_DETAIL, null)
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "sms_receiver_diagnostics"
        private const val KEY_STATUS = "last_status"
        private const val KEY_CATEGORY = "last_category"
        private const val KEY_DETAIL = "last_detail"
        private const val KEY_TIMESTAMP = "last_timestamp"
    }
}
