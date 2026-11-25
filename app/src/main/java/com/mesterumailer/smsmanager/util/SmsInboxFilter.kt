package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.SmsCategory
import com.mesterumailer.smsmanager.model.SmsMessage
import java.util.LinkedHashMap

object SmsInboxFilter {
    private data class SearchIndex(
        val originalBody: String,
        val originalAddress: String,
        val normalizedBody: String,
        val normalizedAddress: String
    )

    // Keeps a small in-memory index for the latest messages so refresh/filter changes
    // do not repeatedly normalize the same Persian/Arabic text.
    private const val MAX_SEARCH_INDEX_ENTRIES = 600
    private val searchIndex = object : LinkedHashMap<Long, SearchIndex>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, SearchIndex>?): Boolean =
            size > MAX_SEARCH_INDEX_ENTRIES
    }

    fun filter(
        messages: List<SmsMessage>,
        visibleCategoryIds: Set<String>,
        query: String
    ): List<SmsMessage> {
        val normalizedQuery = normalize(query)
        return messages.filter { message ->
            message.analysis.categoryId in visibleCategoryIds &&
                matchesNormalizedQuery(message, normalizedQuery)
        }
    }

    /**
     * Accepts an already-normalized query. This is also used while scanning SMS Provider
     * results so the same message text is normalized only once per message id.
     */
    fun matchesNormalizedQuery(message: SmsMessage, normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        val index = synchronized(searchIndex) {
            val existing = searchIndex[message.id]
            if (existing != null &&
                existing.originalBody == message.body &&
                existing.originalAddress == message.address
            ) {
                existing
            } else {
                SearchIndex(
                    originalBody = message.body,
                    originalAddress = message.address,
                    normalizedBody = normalize(message.body),
                    normalizedAddress = normalize(message.address)
                ).also { searchIndex[message.id] = it }
            }
        }
        return index.normalizedBody.contains(normalizedQuery) ||
            index.normalizedAddress.contains(normalizedQuery)
    }

    @JvmName("filterByLegacyCategories")
    fun filter(
        messages: List<SmsMessage>,
        visibleCategories: Set<SmsCategory>,
        query: String
    ): List<SmsMessage> = filter(messages, visibleCategories.map { it.id }.toSet(), query)

    fun normalize(value: String): String = value
        .lowercase()
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('ۀ', 'ه')
        .replace('ة', 'ه')
        .replace('ؤ', 'و')
        .replace('إ', 'ا')
        .replace('أ', 'ا')
        .replace('ٱ', 'ا')
        .replace('۰', '0')
        .replace('۱', '1')
        .replace('۲', '2')
        .replace('۳', '3')
        .replace('۴', '4')
        .replace('۵', '5')
        .replace('۶', '6')
        .replace('۷', '7')
        .replace('۸', '8')
        .replace('۹', '9')
        .replace("\u200c", "")
        .trim()
}
