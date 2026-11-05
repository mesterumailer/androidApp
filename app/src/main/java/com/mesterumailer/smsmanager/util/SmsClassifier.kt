package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsAnalysis
import com.mesterumailer.smsmanager.model.SmsCategory

/**
 * Offline weighted classifier. Phrase evidence is counted once per message;
 * sender evidence is multiplied by senderWeight (default 2).
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

    private val senderWeights = mapOf(
        "transaction" to mapOf("tejaratbank" to 8, "b.qmehriran" to 9, "بانک مهر" to 8, "بانک ملی" to 8, "bank melli" to 8, "بانک تجارت" to 8, "بانک سپه" to 8, "بانک ملت" to 8, "shaparak" to 7),
        "service" to mapOf("تهران پیمنت" to 4, "98200090070" to 4, "digipay" to 3, "دیجی پی" to 3, "کافه ارز" to 3, "ایساکو" to 3),
        "delivery" to mapOf("tipax" to 9, "تیپاکس" to 9, "iran post" to 9, "پست" to 7, "snappfood" to 3),
        "promotion" to mapOf("snapp" to 3, "mci club" to 5, "azkivam" to 6, "dk sellers" to 4, "mci academy" to 5),
        "otp" to mapOf("تهران پیمنت" to 2, "98200090070" to 2)
    )

    fun analyze(address: String, body: String): SmsAnalysis {
        val defaultById = FilterRuleRepository.defaultRules().associateBy { it.categoryId }
        if (rules.any { defaultById[it.categoryId] != it }) {
            return analyzeCustomRules(address, body)
        }
        val sender = normalize(address)
        val text = normalize(body)
        val scores = linkedMapOf<String, Int>()

        rules.forEach { rule ->
            val category = rule.categoryId
            val phraseMap = textWeights[category]
            var score = 0

            if (phraseMap != null) {
                phraseMap.forEach { (phrase, value) ->
                    if (normalize(phrase) in text) score += value
                }
                // Keep user-added rules effective even for built-in categories.
                rule.anyKeywords.distinct().filterNot { phraseMap.containsKey(it) }.forEach {
                    if (normalize(it) in text) score += 3
                }
                rule.requiredKeywords.distinct().forEach {
                    score += if (normalize(it) in text) 5 else -10
                }
                rule.excludedKeywords.distinct().forEach {
                    if (normalize(it) in text) score -= 10
                }
                val knownSenders = senderWeights[category].orEmpty()
                knownSenders.forEach { (alias, value) ->
                    if (normalize(alias) in sender) score += value * senderWeight.coerceIn(0, 5)
                }
                rule.senderContains.distinct().filterNot { alias ->
                    knownSenders.keys.any { normalize(it) == normalize(alias) }
                }.forEach {
                    if (normalize(it) in sender) score += 2 * senderWeight.coerceIn(0, 5)
                }
            } else {
                rule.anyKeywords.distinct().forEach { if (normalize(it) in text) score += 3 }
                rule.requiredKeywords.distinct().forEach { score += if (normalize(it) in text) 5 else -10 }
                rule.excludedKeywords.distinct().forEach { if (normalize(it) in text) score -= 10 }
                rule.senderContains.distinct().forEach { if (normalize(it) in sender) score += 2 * senderWeight.coerceIn(0, 5) }
            }
            scores[category] = score
        }

        val hasCode = numberRegex.containsMatchIn(text)
        val hasOtpIntent = listOf("کد بازیابی", "رمز پویا", "رمز یکبار مصرف", "کد تایید", "کد تأیید", "کد ورود")
            .any { normalize(it) in text }
        if (!hasCode || !hasOtpIntent) scores["otp"] = minOf(scores["otp"] ?: 0, 0)

        val hasAmount = Regex("(?:مبلغ|واریز|برداشت|انتقال|قسط)\\s*[:：]?\\s*[\\d,۰-۹]+|[\\d,۰-۹]+\\s*(?:ریال|تومان)").containsMatchIn(text)
        val hasMoneyAction = listOf("واریز", "برداشت", "انتقال", "خرید", "پرداخت", "قسط:")
            .any { normalize(it) in text }
        if (!hasAmount || !hasMoneyAction) scores["transaction"] = minOf(scores["transaction"] ?: 0, 0)

        val hasParcelContext = listOf("مرسوله", "بسته پستی", "تیپاکس", "tipax", "پستچی", "پست شد")
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

    private fun analyzeCustomRules(address: String, body: String): SmsAnalysis {
        val sender = normalize(address)
        val text = normalize(body)
        val candidates = rules.mapNotNull { rule ->
            if (rule.excludedKeywords.any { normalize(it) in text }) return@mapNotNull null
            val anyHits = rule.anyKeywords.count { normalize(it) in text }
            if (rule.anyKeywords.isNotEmpty() && anyHits < rule.minimumAnyMatches) return@mapNotNull null
            if (rule.requiredKeywords.any { normalize(it) !in text }) return@mapNotNull null
            val senderHits = rule.senderContains.count { normalize(it) in sender }
            if (rule.senderContains.isNotEmpty() && senderHits == 0 && anyHits == 0) return@mapNotNull null
            val value = anyHits * 2 + senderHits * 2 + rule.requiredKeywords.size * 3 + rule.priority.coerceIn(0, 100) / 10
            if (value > 0) rule to value else null
        }.sortedByDescending { it.second }
        val winner = candidates.firstOrNull()?.first
        val categoryId = winner?.categoryId ?: SmsCategory.UNKNOWN.id
        return SmsAnalysis(
            category = SmsCategory.fromId(categoryId),
            categoryId = categoryId,
            otpCode = numberRegex.find(text)?.value,
            amount = null,
            confidence = candidates.firstOrNull()?.second?.let { (it.coerceAtMost(10) / 10f).coerceAtLeast(0.5f) } ?: 0f
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
