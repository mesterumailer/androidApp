package com.mesterumailer.smsmanager.data

import android.content.Context
import com.mesterumailer.smsmanager.model.SmsMessage
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class SmsTrashEntry(
    val key: String,
    val smsId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val categoryId: String,
    val deletedAt: Long
)

class SmsTrashRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<SmsTrashEntry> =
        runCatching {
            val raw = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val entry = SmsTrashEntry(
                        key = item.optString("key").trim(),
                        smsId = item.optLong("smsId", -1L),
                        address = item.optString("address").trim(),
                        body = item.optString("body"),
                        timestamp = item.optLong("timestamp", 0L),
                        categoryId = item.optString("categoryId").trim(),
                        deletedAt = item.optLong("deletedAt", 0L)
                    )
                    if (entry.key.isNotBlank() && entry.timestamp > 0L) {
                        add(entry)
                    }
                }
            }.sortedByDescending { it.deletedAt }
        }.getOrDefault(emptyList())

    fun getRecent(limit: Int = DEFAULT_RECENT_LIMIT): List<SmsTrashEntry> =
        getAll().take(limit.coerceAtLeast(0))

    fun getKeys(): Set<String> =
        preferences.getStringSet(KEY_KEYS, emptySet()).orEmpty().toSet().ifEmpty {
            getAll().mapTo(linkedSetOf()) { it.key }
        }

    fun add(messages: Collection<SmsMessage>) {
        if (messages.isEmpty()) return
        val existing = getAll().associateByTo(linkedMapOf()) { it.key }
        val deletedAt = System.currentTimeMillis()
        messages.forEach { message ->
            val key = keyFor(message)
            existing[key] = SmsTrashEntry(
                key = key,
                smsId = message.id,
                address = message.address,
                body = message.body,
                timestamp = message.timestamp,
                categoryId = message.analysis.categoryId,
                deletedAt = deletedAt
            )
        }
        write(existing.values.sortedByDescending { it.deletedAt })
    }

    fun restore(key: String): Boolean {
        val normalized = key.trim()
        if (normalized.isBlank()) return false
        val current = getAll()
        val updated = current.filterNot { it.key == normalized }
        if (updated.size == current.size) return false
        write(updated)
        return true
    }

    companion object {
        const val DEFAULT_RECENT_LIMIT = 50
        private const val PREFERENCES_NAME = "sms_trash"
        private const val KEY_ITEMS = "items"
        private const val KEY_KEYS = "keys"

        fun keyFor(message: SmsMessage): String =
            keyFor(message.address, message.timestamp, message.body)

        private fun keyFor(address: String, timestamp: Long, body: String): String {
            val value = address.trim() + "|" + timestamp + "|" + body
            return runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                digest.digest(value.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            }.getOrElse {
                value.hashCode().toString()
            }
        }
    }

    private fun write(entries: Collection<SmsTrashEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("key", entry.key)
                put("smsId", entry.smsId)
                put("address", entry.address)
                put("body", entry.body)
                put("timestamp", entry.timestamp)
                put("categoryId", entry.categoryId)
                put("deletedAt", entry.deletedAt)
            })
        }
        preferences.edit()
            .putString(KEY_ITEMS, array.toString())
            .putStringSet(KEY_KEYS, entries.mapTo(linkedSetOf()) { it.key })
            .apply()
    }
}
