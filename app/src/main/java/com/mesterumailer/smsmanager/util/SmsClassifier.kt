package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsAnalysis
import com.mesterumailer.smsmanager.model.SmsCategory

class SmsClassifier(private val rules: List<FilterRule>) {
    private val otpRegex = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
    private val codeForLoginRegex = Regex(
        "کد\\s*[:：]?\\s*\\d{4,8}[\\s\\S]{0,50}(?:ورود|سامانه|وارد\\s+(?:کنید|نمایید))"
    )

    private val otpPhrases = listOf(
        "رمز پویا", "رمز یکبار مصرف", "رمز یک بار مصرف", "رمز یک‌بار مصرف",
        "کد تایید", "کد تأیید", "کد ورود", "کد امنیتی", "کد اعتبارسنجی",
        "verification code", "one-time password", "one time password"
    )

    private val bankSenderMarkers = listOf(
        "bank", "بانک", "melli", "tejarat", "sepah", "shaparak",
        "saman", "pasargad", "resalat", "melat", "mellat"
    )

    private val bankBodyMarkers = listOf(
        "بانک ملی", "بانک سپه", "بانک تجارت", "بانك ملي", "بانک ملت",
        "پرداخت الکترونیک سامان", "شاپرک", "همراه بانک"
    )

    private val explicitMoneyEvents = listOf(
        "واریز مبلغ", "برداشت مبلغ", "انتقال وجه", "کارت به کارت",
        "پرداخت قسط قرارداد", "با موفقیت پرداخت شد", "تراکنش حسابی",
        "واریز:", "برداشت:", "انتقال:"
    )

    private val deliveryStrongMarkers = listOf(
        "مرسوله", "پستچی", "کد رهگیری مرسوله", "رهگیری پست",
        "تحویل پست", "پست شد", "پستی شما", "توزیع مرسوله",
        "پیک", "courier", "shipment", "tracking.post.ir",
        "tracking.post"
    )

    private val promotionMarkers = listOf(
        "تخفیف", "تخفیفی", "حراج", "پیشنهاد ویژه", "کد تخفیف",
        "فروش ویژه", "جشنواره", "کف خرید", "حداقل خرید", "خرید اقساطی",
        "اقساطی", "اعتبارت بخر", "هدیه", "جایزه", "کمپین",
        "ثبت نام رایگان", "رایگان", "تهیه بلیت", "تهیه بلیط",
        "خرید بلیت", "خرید بلیط", "کنسرت", "dj night", "رزرو میز",
        "کلاس", "قبولی", "مصاحبه", "خلاصه رایگان کتاب", "استعلام قیمت",
        "مشاوره تخصصی", "اجرای تخصصی", "کیورد ریسرچ", "سئو",
        "seo", "ابزار geo", "به مشتریای مرتبط", "فروشگاه اینترنتی",
        "بیمه البرز درمان", "ویژه مهندس", "ویژه مهندسان",
        "معرفی کن", "prize"
    )

    fun analyze(address: String, body: String): SmsAnalysis {
        val normalizedBody = normalize(body)
        val normalizedSender = normalize(address)
        val transactionEvidence = hasTransactionEvidence(normalizedSender, normalizedBody)
        val deliveryEvidence = hasDeliveryEvidence(normalizedBody)
        val forcedOtpRule = rules.firstOrNull { it.categoryId == "otp" }
            ?.takeIf { hasOtpEvidence(normalizedBody, transactionEvidence) }

        val candidates = rules.mapNotNull { rule ->
            val score = score(
                rule = rule,
                sender = normalizedSender,
                body = normalizedBody,
                transactionEvidence = transactionEvidence,
                deliveryEvidence = deliveryEvidence
            )
            if (score <= 0) null else rule to score
        }.sortedWith(
            compareByDescending<Pair<FilterRule, Int>> { it.second }
                .thenByDescending { it.first.priority }
        )

        val winningRule = forcedOtpRule ?: candidates.firstOrNull()?.first
        val categoryId = winningRule?.categoryId ?: SmsCategory.UNKNOWN.id
        val category = SmsCategory.fromId(categoryId)

        // Amount extraction remains intentionally disabled.
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
        return otpPhrases.any { normalize(it) in body } ||
            codeForLoginRegex.containsMatchIn(body)
    }

    private fun hasBankContext(sender: String, body: String): Boolean {
        return bankSenderMarkers.any { normalize(it) in sender } ||
            bankBodyMarkers.any { normalize(it) in body }
    }

    private fun hasTransactionEvidence(sender: String, body: String): Boolean {
        if (explicitMoneyEvents.any { normalize(it) in body }) return true

        // Bank statement templates often use compact labels rather than prose.
        val compactDebitOrCredit = Regex(
            "(?:واریز|برداشت|انتقال|قسط)\\s*[:：]\\s*[\\d,]+\\s*[+-]?"
        ).containsMatchIn(body)
        if (compactDebitOrCredit && hasBankContext(sender, body)) return true

        // Purchase SMS from a bank: "خرید / merchant / مبلغ 123,456 / رمز 123456".
        val bankPurchaseWithAmount =
            hasBankContext(sender, body) &&
            "خرید" in body &&
            Regex("مبلغ\\s*[:：]?\\s*[\\d,]+").containsMatchIn(body)
        if (bankPurchaseWithAmount) return true

        // "انتقال به ... مبلغ ..." is a bank transfer even when the word انتقال وجه is absent.
        val bankTransferWithAmount =
            hasBankContext(sender, body) &&
            "انتقال به" in body &&
            Regex("مبلغ\\s*[:：]?\\s*[\\d,]+").containsMatchIn(body)
        if (bankTransferWithAmount) return true

        // Successful installment payment is a financial event, not a service reminder.
        return "پرداخت قسط" in body &&
            ("با موفقیت پرداخت شد" in body || "قرارداد" in body)
    }

    private fun hasDeliveryEvidence(body: String): Boolean {
        // Generic words such as "تحویل" or "رهگیری" are insufficient on their own.
        if (deliveryStrongMarkers.any { normalize(it) in body }) return true

        val orderAndCourier =
            "سفارش" in body &&
            ("پیک" in body || "تحویل داده می شود" in body || "تحویل می شود" in body)
        if (orderAndCourier) return true

        val parcelAndDistribution =
            ("مرسوله" in body || "بسته پستی" in body || "مرسوله پستی" in body) &&
            ("ارسال" in body || "توزیع" in body || "مراجعه" in body || "ثبت گردید" in body)
        return parcelAndDistribution
    }

    private fun score(
        rule: FilterRule,
        sender: String,
        body: String,
        transactionEvidence: Boolean,
        deliveryEvidence: Boolean
    ): Int {
        if (rule.categoryId == "transaction" && !transactionEvidence) return 0
        if (rule.categoryId == "delivery" && !deliveryEvidence) return 0
        if (rule.categoryId == "promotion" &&
            (transactionEvidence || deliveryEvidence && "پیشنهاد" !in body)
        ) return 0
        if (rule.categoryId == "service" && (transactionEvidence || deliveryEvidence)) return 0
        if (rule.excludedKeywords.any { normalize(it) in body }) return 0

        val anyHits = rule.anyKeywords.count { normalize(it) in body }
        if (rule.anyKeywords.isNotEmpty() && anyHits < rule.minimumAnyMatches) return 0

        val requiredMisses = rule.requiredKeywords.count { normalize(it) !in body }
        if (requiredMisses > 0) return 0

        val senderHits = rule.senderContains.count { normalize(it) in sender }
        if (rule.senderContains.isNotEmpty() && senderHits == 0 && anyHits == 0) return 0

        // A sender match is supporting evidence, never enough to label an SMS financial or delivery.
        val score = (anyHits * 2) + (senderHits * 2) + (rule.requiredKeywords.size * 3)
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
