package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsAnalysis
import com.mesterumailer.smsmanager.model.SmsCategory

class SmsClassifier(private val rules: List<FilterRule>) {
    private val otpRegex = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
    private val otpContextRegex = Regex("(?i)(?<![a-z])(?:verification\\s+code|one[- ]time\\s+(?:password|code)|passcode|otp|code)(?![a-z])|کد|رمز")
    private val otpRejectedContexts = listOf(
        "مبلغ", "شماره", "پیگیری", "رهگیری", "مرجع", "شناسه", "تاریخ", "زمان",
        "موجودی", "مانده", "حساب", "کارت", "تراکنش", "فاکتور", "قبض", "تخفیف", "ملی",
        "amount", "tracking", "reference", "transaction", "balance", "account", "card", "invoice", "date", "time", "discount"
    )
    private val maxOtpContextDistance = 32

    fun analyze(address: String, body: String): SmsAnalysis {
        val normalizedBody = normalize(body)
        val normalizedSender = normalize(address)
        val otp = extractOtp(normalizedBody)

        val candidates = rules.mapNotNull { rule ->
            val score = score(rule, normalizedSender, normalizedBody, otp != null)
            if (score <= 0) null else rule to score
        }.sortedWith(
            compareByDescending<Pair<FilterRule, Int>> { it.second }
                .thenByDescending { it.first.priority }
        )

        val winningRule = candidates.firstOrNull()?.first
        val categoryId = winningRule?.categoryId ?: SmsCategory.UNKNOWN.id
        val category = SmsCategory.fromId(categoryId)

        // Amount extraction is intentionally disabled for now to keep Inbox
        // loading/filtering focused on the active classification features.
        val confidence = candidates.firstOrNull()?.second?.let {
            (it.coerceAtMost(10) / 10f).coerceAtLeast(0.5f)
        } ?: 0f

        return SmsAnalysis(
            category = category,
            categoryId = categoryId,
            otpCode = otp,
            amount = null,
            confidence = confidence
        )
    }

    private fun extractOtp(normalizedBody: String): String? {
        val candidates = otpRegex.findAll(normalizedBody).toList()
        if (candidates.isEmpty()) return null

        val contexts = otpContextRegex.findAll(normalizedBody).toList()
        for (context in contexts) {
            val searchStart = context.range.last + 1
            val candidate = candidates.firstOrNull {
                it.range.first >= searchStart &&
                    it.range.first - searchStart <= maxOtpContextDistance &&
                    otpRejectedContexts.none { rejected ->
                        rejected in normalizedBody.substring(searchStart, it.range.first)
                    }
            }
            if (candidate != null) return candidate.value
        }

        return null
    }

    private fun score(
        rule: FilterRule,
        sender: String,
        body: String,
        otpContextDetected: Boolean
    ): Int {
        if (rule.excludedKeywords.any { normalize(it) in body }) return 0

        val anyHits = rule.anyKeywords.count { normalize(it) in body }
        val otpContextMatch = rule.categoryId == SmsCategory.OTP.id && otpContextDetected
        if (rule.anyKeywords.isNotEmpty() && anyHits < rule.minimumAnyMatches && !otpContextMatch) return 0

        val requiredMisses = rule.requiredKeywords.count { normalize(it) !in body }
        if (requiredMisses > 0) return 0

        val senderHits = rule.senderContains.count { normalize(it) in sender }
        if (rule.senderContains.isNotEmpty() && senderHits == 0) return 0

        val score = (anyHits * 2) + (senderHits * 4) + (rule.requiredKeywords.size * 3) +
            if (otpContextMatch) 8 else 0
        return if (score > 0) score else 0
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
