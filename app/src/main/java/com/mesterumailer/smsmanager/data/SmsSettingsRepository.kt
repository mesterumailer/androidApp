package com.mesterumailer.smsmanager.data

import android.content.Context
import java.util.Calendar
import java.util.GregorianCalendar

enum class InboxReadMode(val id: String, val label: String) {
    UNTIL_TODAY("until_today", "تا امروز"),
    UNTIL_DATE("until_date", "تا تاریخ مشخص");

    companion object {
        fun fromId(id: String?): InboxReadMode =
            values().firstOrNull { it.id == id } ?: UNTIL_TODAY
    }
}

data class InboxReadSettings(
    val limit: Int,
    val mode: InboxReadMode,
    val untilDateStartMillis: Long?
)

class SmsSettingsRepository(context: Context) {
    companion object {
        const val DEFAULT_LIMIT = 1000
        const val MIN_LIMIT = 200
        const val MAX_LIMIT = 5000

        const val DEFAULT_INBOX_LIMIT = DEFAULT_LIMIT
        const val MIN_INBOX_LIMIT = MIN_LIMIT
        const val MAX_INBOX_LIMIT = MAX_LIMIT

        private const val PREFS = "sms_settings"
        private const val KEY_INBOX_LIMIT = "inbox_limit"
        private const val KEY_INBOX_READ_MODE = "inbox_read_mode"
        private const val KEY_INBOX_DATE_START = "inbox_date_start"
    }

    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getInboxLimit(): Int =
        preferences.getInt(KEY_INBOX_LIMIT, DEFAULT_LIMIT)
            .coerceIn(MIN_LIMIT, MAX_LIMIT)

    fun setInboxLimit(value: Int) {
        preferences.edit()
            .putInt(KEY_INBOX_LIMIT, value.coerceIn(MIN_LIMIT, MAX_LIMIT))
            .apply()
    }

    fun getInboxReadMode(): InboxReadMode =
        InboxReadMode.fromId(
            preferences.getString(KEY_INBOX_READ_MODE, InboxReadMode.UNTIL_TODAY.id)
        )

    fun setInboxReadMode(mode: InboxReadMode) {
        preferences.edit()
            .putString(KEY_INBOX_READ_MODE, mode.id)
            .apply()
    }

    fun getInboxDateStartMillis(): Long? =
        preferences.getLongOrNull(KEY_INBOX_DATE_START)

    fun setInboxDate(year: Int, month: Int, dayOfMonth: Int) {
        val start = GregorianCalendar(year, month, dayOfMonth).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        setInboxDateStartMillis(start)
    }

    fun setInboxDate(timestampMillis: Long) {
        val calendar = GregorianCalendar().apply {
            timeInMillis = timestampMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        setInboxDateStartMillis(calendar.timeInMillis)
    }

    private fun setInboxDateStartMillis(startMillis: Long) {
        preferences.edit()
            .putLong(KEY_INBOX_DATE_START, startMillis)
            .apply()
    }

    fun getInboxReadSettings(): InboxReadSettings =
        InboxReadSettings(
            limit = getInboxLimit(),
            mode = getInboxReadMode(),
            untilDateStartMillis = getInboxDateStartMillis()
        )
}

private fun android.content.SharedPreferences.getLongOrNull(key: String): Long? =
    if (contains(key)) getLong(key, 0L) else null
