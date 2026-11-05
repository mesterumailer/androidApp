package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsAnalysis
import com.mesterumailer.smsmanager.model.SmsCategory

class SmsClassifier(private val rules: List<FilterRule>) {
    private val otpRegex = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
    private val codeForLoginRegex = Regex(
        "کد\\\\s*[:：]?\\\\s*\\\\d{4,8}[\\\\s\\\\S]{0,50}(?:ورود|سامانه|وارد\\\\s+(?:کنید|نمایید))"
    )

    private val otpPhrases = listOf(
        "رمز پویا", "رمز یکبار مصرف", "رمز یک بار مصرف", "رمز یک‌بار مصرف",
        "کد تایید", "کد تأیید", "کد ورود", "کد امنیتی", "کد اعتبارسنجی",
        "verification code", "one-time password", "one time password"
    )

    private val transactionEvidencePhrases = listOf(
        "واریز:", "واریز مبلغ", "برداشت:", "برداشت مبلغ", "انتقال وجه", "انتقال به",
        "کارت به کارت", "خرید به مبلغ", "خرید با کارت", "پرداخت قسط قرارداد",
        "پرداخت قسط", "با موفقیت پرداخت شد"
    )

    fun analyze(address: String, body: String): SmsAnalysis {
        val normalizedBody = normalize(body)
        val normalizedSender = normalize(address)
        val transactionEvidence = hasTransactionEvidence(normalizedBody)
        val forcedOtpRule = rules.firstOrNull { it.categoryId == "otp" }
            ?.takeIf { hasOtpEvidence(normalizedBody, transactionEvidence) }

        val candidates = rules.mapNotNull { rule ->
            val score = score(rule, normalizedSender, normalizedBody, transactionEvidence)
            if (score <= 0) null else rule to score
        }.sortedWith(
            compareByDescending<Pair<FilterRule, Int>> { it.second }
                .thenByDescending { it.first.priority }
        )

        val winningRule = forcedOtpRule ?: candidates.firstOrNull()?.first
        val categoryId = winningRule?.categoryId ?: SmsCategory.UNKNOWN.id
        val category = SmsCategory.fromId(categoryId)

        // Amount extraction is intentionally disabled for now to keep Inbox
        // loading/filtering focused on the active classification features.
        val otp = otpRegex.find(normalizedBody)?.value
        val confidence = if (forcedOtpRule != null) {
            1f
        } else {
            candidates.firstOrNull()?.second?.let {
                (it.coerceAtMost(10) / 10f).coerceAtLeast(0.5f)
            } ?: 0f
        }

        return SmsAnalysis(
            category = category,
            categoryId = categoryId,
            otpCode = otp,
            amount = null,
            confidence = confidence
        )
    }

    private fun hasOtpEvidence(body: String, transactionEvidence: Boolean): Boolean {
        if (transactionEvidence || !otpRegex.containsMatchIn(body)) return false
        return otpPhrases.any { normalize(it) in body } || codeForLoginRegex.containsMatchIn(body)
    }

    private fun hasTransactionEvidence(body: String): Boolean {
        if (transactionEvidencePhrases.any { normalize(it) in body }) return true
        return Regex("(?:واریز|برداشت)\\\\s*[:：]?\\\\s*[\\\\d,]+").containsMatchIn(body)
    }

    private fun score(
        rule: FilterRule,
        sender: String,
        body: String,
        transactionEvidence: Boolean
    ): Int {
        if (rule.categoryId == "transaction" && !transactionEvidence) return 0
        if (rule.categoryId == "promotion" && transactionEvidence) return 0
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
