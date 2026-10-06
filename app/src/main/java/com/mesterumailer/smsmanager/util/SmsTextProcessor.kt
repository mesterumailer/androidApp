package com.mesterumailer.smsmanager.util

object SmsTextProcessor {
    private val webUrlPattern = Regex(
        "(?i)(?:(?:https?://|www\\.)[^\\s<>()]+|(?:[a-z0-9-]+\\.)+[a-z]{2,63}(?:/[^\\s<>()]*)?)"
    )

    fun mayContainWebUrl(text: String): Boolean = webUrlPattern.containsMatchIn(text)
}
