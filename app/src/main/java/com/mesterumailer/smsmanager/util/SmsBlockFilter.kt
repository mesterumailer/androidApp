package com.mesterumailer.smsmanager.util

/**
 * A message is blocked when any configured sender pattern matches its address
 * or any configured content phrase occurs in its body.
 */
class SmsBlockFilter(
    blockedSenders: Collection<String>,
    blockedContent: Collection<String>
) {
    private val senderPatterns = blockedSenders.map(::normalize).filter { it.isNotBlank() }
    private val contentPatterns = blockedContent.map(::normalize).filter { it.isNotBlank() }

    fun isBlocked(address: String, body: String): Boolean {
        val sender = normalize(address)
        val text = normalize(body)
        return senderPatterns.any { it in sender } || contentPatterns.any { it in text }
    }

    private fun normalize(value: String): String = value
        .map {
            when (it) {
                '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
                '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> it
            }
        }
        .joinToString("")
        .lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace(Regex("[\u200c\u200d]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
