package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsAnalysis
import com.mesterumailer.smsmanager.model.SmsCategory

/**
 * Offline weighted classifier. Phrase evidence is counted once per message.
 * Sender identities and their individual weights are supplied by editable category rules.
 */
class SmsClassifier(
    private val rules: List<FilterRule>,
    private val senderWeight: Int = DEFAULT_SENDER_WEIGHT
) {
    private val numberRegex = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

    private val textWeights = mapOf(
        "otp" to weights(
            10 to listOf("کد بازیابی رمز عبور", "کد بازیابی", "رمز پویا", "رمز یکبار مصرف", "رمز یک بار مصرف", "کد تایید", "کد تأیید", "کد ورود", "کد امنیتی", "کد اعتبارسنجی", "verification code"),
            8 to listOf("بازیابی رمز", "جهت ورود", "وارد نمایید", "وارد کنید"),
            -10 to listOf("کد تخفیف", "کد درگاه")
        ),
        "transaction" to weights(
            10 to listOf("واریز حقوق", "برداشت مبلغ", "واریز مبلغ", "انتقال وجه", "انتقال به", "کارت به کارت", "تراکنش حسابی", "با موفقیت پرداخت شد"),
            9 to listOf("قسط:", "انتقال:", "واریز:", "برداشت:", "خرید مجموعه", "خرید دیجی کالا"),
            7 to listOf("مبلغ", "پرداخت", "خرید", "واریز", "برداشت", "قسط"),
            3 to listOf("رمز", "کارت"),
            -10 to listOf("پیشنهاد وام", "وام ویژه", "خرید اقساطی", "پرداخت اقساطی", "دریافت وام", "کد بازیابی رمز عبور", "پرداخت بدهی", "اعتبار قابل مصرف")
        ),
        "delivery" to weights(
            10 to listOf("کد رهگیری مرسوله", "مرسوله پستی", "موقعیت گیرنده بسته", "پستچی", "توزیع مرسوله", "تحویل پست", "رهگیری سفارش"),
            8 to listOf("ارسال خواهد گردید", "به زودی به شما تحویل", "پست شد", "بسته با کد", "پیک"),
            6 to listOf("مرسوله", "بسته پستی", "تیپاکس", "tipax"),
            -8 to listOf("تحویل گرفتید", "تحویل بگیرید")
        ),
        "promotion" to weights(
            10 to listOf("پیشنهاد ویژه", "تخفیف", "جایزه", "کمپین", "قرعه کشی", "رزرو میز", "تهیه بلیت", "کنسرت", "dj night", "ثبت نام رایگان", "خرید میوه", "خرید صبحانه", "خلاصه رایگان کتاب", "نشست تخصصی", "پخش زنده", "وام بانکی ویژه", "دریافت وام", "ویژه مهندس"),
            8 to listOf("هدیه", "شانس", "امتیاز", "پیشنهاد", "آموزش", "کلاس", "مصاحبه", "سئو", "کیورد ریسرچ", "بازارگان", "رایگان", "اقساطی"),
            6 to listOf("لغو 11", "لغو۱۱", "همین الان", "ثبت نام", "خرید"),
            -10 to listOf("تراکنش حسابی", "واریز حقوق", "با موفقیت پرداخت شد", "کد بازیابی رمز عبور", "مرسوله پستی")
        ),
        "service" to weights(
            10 to listOf("نظرسنجی", "تجربه استفاده", "وضعیت انجام شده", "کنسل شد", "تیکت شماره", "پاسخ داده شد", "ورود به سایت", "ورود به سامانه", "کارت بانکی", "در وضعیت تایید", "در وضعیت در حال بررسی", "اعتبار قابل مصرف", "مالیات عملکرد", "پلیس امنیت اقتصادی"),
            8 to listOf("اطلاع رسانی", "پشتیبانی", "راهنمای مشتری", "سامانه", "ثبت نام تکمیلی", "ستاد خبری", "تجربه شما", "خدمات"),
            5 to listOf("درخواست", "اعلان", "ورود"),
            -10 to listOf("تخفیف", "جایزه", "پیشنهاد ویژه", "خرید میوه", "پخش زنده", "دریافت وام")
        )
    )

    /**
     * Returns all phrase weights used for this category, including built-in defaults,
     * the category's own rule phrases, and the user's explicit overrides.
     */
    fun keywordWeightsFor(rule: FilterRule): Map<String, Int> = buildMap {
        textWeights[rule.categoryId].orEmpty().forEach { (phrase, value) -> put(phrase, value) }
        rule.anyKeywords.distinct().forEach { putIfAbsent(it, 3) }
        rule.requiredKeywords.distinct().forEach { putIfAbsent(it, 5) }
        rule.excludedKeywords.distinct().forEach { put(it, -10) }
        val allowed = (keys + rule.anyKeywords + rule.requiredKeywords + rule.excludedKeywords).toSet()
        rule.keywordWeights.filterKeys { it in allowed }.forEach { (phrase, value) ->
            put(phrase, value.coerceIn(-20, 20))
        }
    }

    /**
     * Sender aliases belong to the editable category rule, never to this classifier.
     * Generic aliases receive the standard fallback score; explicit per-sender values win.
     */
    fun senderWeightsFor(rule: FilterRule): Map<String, Int> = buildMap {
        rule.senderContains.distinct().forEach {
            put(it, 2 * senderWeight.coerceIn(0, 5))
        }
        val allowed = rule.senderContains.toSet()
        rule.senderWeights.filterKeys { it in allowed }.forEach { (alias, value) ->
            put(alias, value.coerceIn(-20, 20))
        }
    }

    fun analyze(address: String, body: String): SmsAnalysis {
        val sender = normalize(address)
        val text = normalize(body)
        val scores = linkedMapOf<String, Int>()

        rules.forEach { rule ->
            val category = rule.categoryId
            var score = 0
            keywordWeightsFor(rule).forEach { (phrase, value) ->
                if (value != 0 && normalize(phrase) in text) score += value
            }
            rule.requiredKeywords.distinct().forEach {
                if (normalize(it) !in text) score -= 10
            }
            senderWeightsFor(rule).forEach { (alias, value) ->
                if (value != 0 && normalize(alias) in sender) score += value
            }
            // Minimum-match and priority settings now modify scores in this same engine.
            if (rule.minimumAnyMatches > 1) {
                val anyHits = rule.anyKeywords.distinct().count { normalize(it) in text }
                if (anyHits < rule.minimumAnyMatches) score -= 10
            }
            if (category !in textWeights.keys && score > 0) {
                score += rule.priority.coerceIn(0, 100) / 10
            }
            scores[category] = score
        }

        val hasCode = numberRegex.containsMatchIn(text)
        val hasOtpIntent = listOf("کد بازیابی", "رمز پویا", "رمز یکبار مصرف", "کد تایید", "کد تأیید", "کد ورود", "جهت ورود", "وارد نمایید")
            .any { normalize(it) in text } && "کد" in text
        if (!hasCode || !hasOtpIntent) scores["otp"] = minOf(scores["otp"] ?: 0, 0)

        val hasAmount = Regex("(?:مبلغ|واریز|برداشت|انتقال|قسط)\\s*[:：]?\\s*[\\d,۰-۹]+|[\\d,۰-۹]+\\s*(?:ریال|تومان)").containsMatchIn(text)
        val hasMoneyAction = listOf("واریز", "برداشت", "انتقال", "خرید", "پرداخت", "قسط:", "تراکنش")
            .any { normalize(it) in text }

        // A numeric authentication code with explicit OTP intent must beat generic
        // login/service wording, unless the same SMS clearly describes a money action.
        if (hasCode && hasOtpIntent && !(hasAmount && hasMoneyAction)) {
            scores["otp"] = maxOf(
                scores["otp"] ?: 0,
                (scores["service"] ?: 0) + MINIMUM_SCORE_MARGIN + 1
            )
        }

        val completedInstallment = "پرداخت قسط قرارداد" in text && "با موفقیت پرداخت شد" in text
        if ((!hasAmount || !hasMoneyAction) && !completedInstallment) scores["transaction"] = minOf(scores["transaction"] ?: 0, 0)

        val hasParcelContext = listOf("مرسوله", "بسته پستی", "تیپاکس", "tipax", "پستچی", "پست شد", "رهگیری سفارش", "توسط پیک")
            .any { normalize(it) in text }
        if (!hasParcelContext) scores["delivery"] = minOf(scores["delivery"] ?: 0, 0)

        val ranked = scores.entries.sortedByDescending { it.value }
        val first = ranked.firstOrNull()?.takeIf { it.value > 0 }
        val second = ranked.getOrNull(1)
        val winner = first?.takeIf { second == null || it.value - second.value >= MINIMUM_SCORE_MARGIN }
        val categoryId = winner?.key ?: SmsCategory.UNKNOWN.id
        val confidence = winner?.let { (it.value.toFloat() / (it.value + (second?.value?.coerceAtLeast(0) ?: 0) + 1)).coerceIn(0f, 1f) } ?: 0f

        return SmsAnalysis(
            category = SmsCategory.fromId(categoryId),
            categoryId = categoryId,
            otpCode = if (categoryId == "otp") numberRegex.find(text)?.value else null,
            amount = null,
            confidence = confidence
        )
    }

    private fun weights(vararg groups: Pair<Int, List<String>>): Map<String, Int> =
        buildMap { groups.forEach { (value, phrases) -> phrases.forEach { put(it, value.coerceIn(-10, 10)) } } }

    private fun normalize(value: String): String = normalizeDigits(value).lowercase()
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace(Regex("[\\u200c\\u200d]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun normalizeDigits(value: String): String = value.map {
        when (it) {
            '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
            '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
            '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
            '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
            else -> it
        }
    }.joinToString("")

    companion object {
        const val DEFAULT_SENDER_WEIGHT = 2
        const val MINIMUM_SCORE_MARGIN = 2
    }
}
