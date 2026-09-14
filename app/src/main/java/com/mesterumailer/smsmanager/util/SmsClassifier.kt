package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsAnalysis
import com.mesterumailer.smsmanager.model.SmsCategory

class SmsClassifier(private val rules: List<FilterRule>) {
    private val otpRegex = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
    private val amountRegex = Regex("(?i)(?:amount|مبلغ|برداشت|واریز|خرید|پرداخت)\\D{0,20}([\\d,.]+)")

    fun analyze(address: String, body: String): SmsAnalysis {
        val normalizedBody = normalize(body)
        val normalizedSender = normalize(address)

        val candidates = rules.mapNotNull { rule ->
            val score = score(rule, normalizedSender, normalizedBody)
            if (score <= 0) null else rule to score
        }.sortedWith(
            compareByDescending<Pair<FilterRule, Int>> { it.second }
                .thenByDescending { it.first.priority }
        )

        val winningRule = candidates.firstOrNull()?.first
        val categoryId = winningRule?.categoryId ?: SmsCategory.UNKNOWN.id
        val category = SmsCategory.fromId(categoryId)

        val normalizedDigits = normalizeDigits(body)
        val otp = otpRegex.find(normalizedDigits)?.value
        val amount = amountRegex.find(normalizedDigits)?.groupValues?.getOrNull(1)
        val confidence = candidates.firstOrNull()?.second?.let {
            (it.coerceAtMost(10) / 10f).coerceAtLeast(0.5f)
        } ?: 0f

        return SmsAnalysis(
            category = category,
            categoryId = categoryId,
            otpCode = otp,
            amount = amount,
            confidence = confidence
        )
    }

    private fun score(rule: FilterRule, sender: String, body: String): Int {
        if (rule.excludedKeywords.any { normalize(it) in body }) return 0

        val anyHits = rule.anyKeywords.count { normalize(it) in body }
        if (rule.anyKeywords.isNotEmpty() && anyHits < rule.minimumAnyMatches) return 0

        val requiredMisses = rule.requiredKeywords.count { normalize(it) !in body }
        if (requiredMisses > 0) return 0

        val senderHits = rule.senderContains.count { normalize(it) in sender }
        if (rule.senderContains.isNotEmpty() && senderHits == 0 && anyHits == 0) return 0

        val score = (anyHits * 2) + (senderHits * 4) + (rule.requiredKeywords.size * 3)
        return if (score > 0) score + rule.priority.coerceIn(0, 100) / 10 else 0
    }

    private fun normalize(value: String): String = normalizeDigits(value)
        .lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace(Regex("[\\u200c\\u200d]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeDigits(value: String): String = value.map {
        when (it) {
            '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
            '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
            '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
            '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
            else -> it
        }
    }.joinToString("")
}
