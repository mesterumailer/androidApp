package com.mesterumailer.smsmanager.data

import android.content.Context
import org.json.JSONArray

data class SmsBlockSettings(
    val blockedSenders: List<String> = emptyList(),
    val blockedContent: List<String> = emptyList()
)

class SmsBlockRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): SmsBlockSettings = SmsBlockSettings(
        blockedSenders = readList(KEY_SENDERS),
        blockedContent = readList(KEY_CONTENT)
    )

    fun addBlockedSender(value: String): Boolean = add(KEY_SENDERS, value)

    fun removeBlockedSender(value: String): Boolean = remove(KEY_SENDERS, value)

    fun addBlockedContent(value: String): Boolean = add(KEY_CONTENT, value)

    fun removeBlockedContent(value: String): Boolean = remove(KEY_CONTENT, value)

    fun clearAll() {
        preferences.edit().remove(KEY_SENDERS).remove(KEY_CONTENT).apply()
    }

    private fun add(key: String, value: String): Boolean {
        val normalized = value.trim()
        if (normalized.isBlank()) return false
        val current = readList(key)
        if (current.any { it.equals(normalized, ignoreCase = true) }) return false
        writeList(key, current + normalized)
        return true
    }

    private fun remove(key: String, value: String): Boolean {
        val current = readList(key)
        val updated = current.filterNot { it.equals(value.trim(), ignoreCase = true) }
        if (updated.size == current.size) return false
        writeList(key, updated)
        return true
    }

    private fun readList(key: String): List<String> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    array.optString(i).trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    private fun writeList(key: String, values: List<String>) {
        val array = JSONArray()
        values.distinct().forEach(array::put)
        preferences.edit().putString(key, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "sms_block_filters"
        private const val KEY_SENDERS = "blocked_senders"
        private const val KEY_CONTENT = "blocked_content"
    }
}
