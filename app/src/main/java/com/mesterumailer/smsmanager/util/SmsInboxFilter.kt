package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.SmsCategory
import com.mesterumailer.smsmanager.model.SmsMessage

object SmsInboxFilter {
    fun filter(
        messages: List<SmsMessage>,
        visibleCategories: Set<SmsCategory>,
        query: String
    ): List<SmsMessage> {
        val normalizedQuery = normalize(query)
        return messages.filter { message ->
            message.analysis.category in visibleCategories &&
                (normalizedQuery.isBlank() ||
                    normalize(message.body).contains(normalizedQuery) ||
                    normalize(message.address).contains(normalizedQuery))
        }
    }

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
