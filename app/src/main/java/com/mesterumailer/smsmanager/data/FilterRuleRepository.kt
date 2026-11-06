package com.mesterumailer.smsmanager.data

import android.content.Context
import com.mesterumailer.smsmanager.model.FilterRule
import org.json.JSONArray

class FilterRuleRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadRules(): List<FilterRule> {
        val stored = preferences.getString(KEY_RULES, null) ?: return defaultRulesForCurrentSenderWeight()
        val loaded = runCatching {
            val array = JSONArray(stored)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    add(FilterRule.fromJson(array.getJSONObject(i)))
                }
            }.filter { it.categoryId.isNotBlank() && it.displayName.isNotBlank() }
        }.getOrElse { return defaultRulesForCurrentSenderWeight() }

        return migrateUnmodifiedLegacyDefaults(loaded)
    }

    private fun migrateUnmodifiedLegacyDefaults(rules: List<FilterRule>): List<FilterRule> {
        val legacy = (legacyDefaultRules() + previousReleaseDefaultRules()).groupBy { it.categoryId }
        val current = defaultRules().associateBy { it.categoryId }
        val multiplier = getSenderWeight()
        val migrated = rules.map { rule ->
            val wasUnmodifiedBuiltIn = legacy[rule.categoryId].orEmpty().any { it == rule }
            val source = if (wasUnmodifiedBuiltIn) current[rule.categoryId] ?: rule else rule
            migrateBuiltInSenderList(source, multiplier, replaceBuiltInWeights = wasUnmodifiedBuiltIn)
        }
        if (migrated != rules) saveRules(migrated)
        return migrated
    }

    /**
     * Moves the former classifier-owned sender aliases into each category's editable
     * FilterRule list. Existing user weights always win; only missing defaults are migrated.
     */
    private fun migrateBuiltInSenderList(
        rule: FilterRule,
        multiplier: Int,
        replaceBuiltInWeights: Boolean = false
    ): FilterRule {
        val template = defaultRules().firstOrNull { it.categoryId == rule.categoryId } ?: return rule
        val senders = (rule.senderContains + template.senderWeights.keys)
            .distinctBy { it.trim().lowercase() }
        val weights = rule.senderWeights.toMutableMap()
        template.senderWeights.forEach { (alias, defaultFinalWeight) ->
            val existing = weights.keys.firstOrNull { it.trim().equals(alias.trim(), ignoreCase = true) }
            if (replaceBuiltInWeights || existing == null) {
                // Template weights are based on the former default multiplier (2).
                weights[existing ?: alias] = (defaultFinalWeight / DEFAULT_SENDER_WEIGHT) * multiplier
            }
        }
        return rule.copy(senderContains = senders, senderWeights = weights)
    }

    private fun defaultRulesForCurrentSenderWeight(): List<FilterRule> {
        val multiplier = getSenderWeight()
        return defaultRules().map { migrateBuiltInSenderList(it, multiplier, replaceBuiltInWeights = true) }
    }

    fun getSenderWeight(): Int = preferences.getInt(KEY_SENDER_WEIGHT, 2).coerceIn(0, 5)

    fun setSenderWeight(weight: Int) {
        preferences.edit().putInt(KEY_SENDER_WEIGHT, weight.coerceIn(0, 5)).apply()
    }

    fun saveRules(rules: List<FilterRule>) {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_RULES, array.toString()).apply()
    }

    fun addRule(rule: FilterRule): Boolean {
        val rules = loadRules()
        if (rules.any { it.categoryId == rule.categoryId }) return false
        saveRules(rules + rule)
        return true
    }

    fun updateRule(rule: FilterRule): Boolean {
        val rules = loadRules()
        if (rules.none { it.categoryId == rule.categoryId }) return false
        saveRules(rules.map { if (it.categoryId == rule.categoryId) rule else it })
        return true
    }

    fun deleteRule(categoryId: String): Boolean {
        val rules = loadRules()
        val updated = rules.filterNot { it.categoryId == categoryId }
        if (updated.size == rules.size) return false
        saveRules(updated)
        return true
    }

    fun resetToDefaults() = saveRules(defaultRulesForCurrentSenderWeight())

    companion object {
        private const val PREFERENCES_NAME = "sms_manager_settings"
        private const val KEY_RULES = "filter_rules"
        private const val KEY_SENDER_WEIGHT = "classification_sender_weight"
        private const val DEFAULT_SENDER_WEIGHT = 2

        fun defaultRules(): List<FilterRule> = listOf(
            FilterRule(
                categoryId = "otp",
                displayName = "کد تأیید",
                senderContains = listOf("verify", "otp", "auth", "امنیت", "تایید", "تأیید", "تهران پیمنت", "98200090070"),
                senderWeights = mapOf("تهران پیمنت" to 4, "98200090070" to 4),
                anyKeywords = listOf(
                    "رمز پویا", "رمز یکبار مصرف", "رمز یک‌بار مصرف", "کد تایید", "کد تأیید",
                    "کد ورود", "کد فعالسازی", "کد امنیتی", "verification code", "one-time", "otp", "code"
                ),
                excludedKeywords = listOf("تخفیف", "حراج", "کد درگاه"),
                minimumAnyMatches = 1,
                priority = 100
            ),
            FilterRule(
                categoryId = "promotion",
                displayName = "تبلیغاتی",
                senderContains = listOf("ads", "advert", "marketing", "promo", "snapp", "mci club", "azkivam", "dk sellers", "mci academy"),
                senderWeights = mapOf("snapp" to 6, "mci club" to 10, "azkivam" to 12, "dk sellers" to 8, "mci academy" to 10),
                anyKeywords = listOf(
                    "تخفیف", "تخفیفی", "حراج", "پیشنهاد ویژه", "کد تخفیف", "فروش ویژه", "جشنواره",
                    "کف خرید", "حداقل خرید", "خرید اقساطی", "اقساطی", "اعتبارت بخر", "هدیه",
                    "بسته ویژه", "بعدا بده", "بعداً بده", "آخر ماه پرداخت",
                    "discount", "sale", "offer", "promo", "promotion", "coupon", "%",
                    "کنسرت", "تهیه بلیت", "تهیه بلیط", "خرید بلیت", "خرید بلیط", "dj night",
                    "رزرو میز", "کلاس", "قبولی", "مصاحبه", "آموزش", "خلاصه رایگان کتاب",
                    "استعلام قیمت", "مشاوره تخصصی", "اجرای تخصصی", "ثبت نام رایگان", "رایگان",
                    "جایزه", "کمپین", "معرفی کن", "کسب و کار", "به مشتریای مرتبط",
                    "ویژه مهندس", "ویژه مهندسان", "جمع آوری با الوپست", "بیمه البرز درمان",
                    "کیورد ریسرچ", "سئو", "seo", "ابزار geo", "prize"
                ),
                excludedKeywords = listOf(
                    "تراکنش", "واریز مبلغ", "برداشت مبلغ", "انتقال وجه", "انتقال به",
                    "کارت به کارت", "خرید به مبلغ", "خرید با کارت", "پرداخت قسط قرارداد",
                    "پرداخت قسط", "با موفقیت پرداخت شد", "رمز پویا", "کد تأیید", "کد تایید"
                ),
                minimumAnyMatches = 1,
                priority = 60
            ),
            FilterRule(
                categoryId = "service",
                displayName = "خدمات",
                senderContains = listOf("service", "support", "notify", "سرویس", "policefata", "تهران پیمنت", "98200090070", "digipay", "دیجی پی", "کافه ارز", "ایساکو"),
                senderWeights = mapOf("تهران پیمنت" to 8, "98200090070" to 8, "digipay" to 6, "دیجی پی" to 6, "کافه ارز" to 6, "ایساکو" to 6),
                anyKeywords = listOf(
                    "فعال سازی", "فعالسازی", "غیرفعال سازی", "غیرفعالسازی", "اشتراک", "تمدید",
                    "قبض", "یادآوری", "اعلان", "درخواست", "خدمات", "اختلال", "سرویس دهی",
                    "ورود به سامانه", "وارد همراه بانک", "وارد سامانه", "اطلاع رسانی",
                    "شهروند گرامی", "کلاهبرداری", "کلاهبرداران", "هک گوشی", "سرقت موجودی",
                    "هشدار", "اخطار", "پشتیبانی",
                    "service", "subscription", "renewal", "support", "notification",
                    "مودی گرامی", "سازمان امور مالیاتی", "مالیات عملکرد", "تبصره ماده 100",
                    "سفارش شماره", "وضعیت انجام شده", "کنسل شد", "عدم پرداخت",
                    "تیکت شماره", "پاسخ داده شد", "نظرسنجی", "تجربه استفاده", "تجربه شما",
                    "همکار عزیز", "پلیس امنیت اقتصادی", "ستاد خبری", "سامانه مالیاتی",
                    "ثبت نام تکمیلی"
                ),
                excludedKeywords = listOf(
                    "تخفیف", "حراج", "واریز مبلغ", "برداشت مبلغ", "انتقال وجه",
                    "انتقال به", "کارت به کارت", "خرید به مبلغ", "خرید با کارت", "پرداخت قسط"
                ),
                minimumAnyMatches = 1,
                priority = 50
            ),
            FilterRule(
                categoryId = "delivery",
                displayName = "ارسال و تحویل",
                senderContains = listOf("post", "delivery", "courier", "پست", "ارسال", "مرسوله", "tipax", "تیپاکس", "iran post", "snappfood"),
                senderWeights = mapOf("tipax" to 18, "تیپاکس" to 18, "iran post" to 18, "پست" to 14, "snappfood" to 6),
                anyKeywords = listOf(
                    "مرسوله", "پستچی", "پستی", "بسته پستی", "تحویل پست", "پست شد",
                    "توزیع مرسوله", "کد رهگیری مرسوله", "رهگیری پست", "پیک",
                    "delivery", "tracking", "shipment", "courier"
                ),
                minimumAnyMatches = 1,
                priority = 35
            ),
            FilterRule(
                categoryId = "transaction",
                displayName = "تراکنش مالی",
                senderContains = listOf("bank", "بانک", "shaparak", "payment", "card", "tejaratbank", "b.qmehriran", "بانک مهر", "بانک ملی", "bank melli", "بانک تجارت", "بانک سپه", "بانک ملت"),
                senderWeights = mapOf("tejaratbank" to 16, "b.qmehriran" to 18, "بانک مهر" to 16, "بانک ملی" to 16, "bank melli" to 16, "بانک تجارت" to 16, "بانک سپه" to 16, "بانک ملت" to 16, "shaparak" to 14),
                anyKeywords = listOf(
                    "واریز:", "واریز مبلغ", "برداشت:", "برداشت مبلغ", "انتقال وجه", "انتقال به",
                    "کارت به کارت", "خرید به مبلغ", "خرید با کارت", "پرداخت قسط قرارداد",
                    "پرداخت قسط", "با موفقیت پرداخت شد",
                    "deposit", "withdrawal", "money transfer", "card to card",
                    "خرید", "مبلغ", "قسط:", "قسط", "انتقال:"
                ),
                minimumAnyMatches = 1,
                priority = 30
            )
        )

        private fun legacyDefaultRules(): List<FilterRule> = listOf(
            FilterRule(
                categoryId = "otp",
                displayName = "کد تأیید",
                senderContains = listOf("verify", "otp", "auth", "امنیت", "تایید", "تأیید"),
                anyKeywords = listOf(
                    "رمز پویا", "رمز یکبار مصرف", "کد تایید", "کد تأیید", "کد ورود", "کد فعالسازی",
                    "verification code", "one-time", "otp", "code"
                ),
                excludedKeywords = listOf("تخفیف", "حراج"),
                minimumAnyMatches = 1,
                priority = 50
            ),
            FilterRule(
                categoryId = "promotion",
                displayName = "تبلیغاتی",
                senderContains = listOf("ads", "advert", "marketing", "promo"),
                anyKeywords = listOf(
                    "تخفیف", "حراج", "پیشنهاد ویژه", "کد تخفیف", "فروش ویژه", "جشنواره",
                    "discount", "sale", "offer", "promo", "promotion", "coupon", "%"
                ),
                excludedKeywords = listOf("تراکنش", "برداشت", "واریز", "رمز پویا", "کد تأیید", "کد تایید"),
                minimumAnyMatches = 1,
                priority = 10
            ),
            FilterRule(
                categoryId = "service",
                displayName = "خدمات",
                senderContains = listOf("service", "support", "notify", "سرویس"),
                anyKeywords = listOf(
                    "فعال سازی", "فعالسازی", "غیرفعال سازی", "غیرفعالسازی", "اشتراک", "تمدید",
                    "قبض", "یادآوری", "اعلان", "درخواست", "خدمات", "service", "subscription", "renewal", "support", "notification"
                ),
                excludedKeywords = listOf("تخفیف", "حراج", "برداشت", "واریز", "خرید", "تراکنش"),
                minimumAnyMatches = 1,
                priority = 20
            ),
            FilterRule(
                categoryId = "delivery",
                displayName = "ارسال و تحویل",
                senderContains = listOf("post", "delivery", "courier", "پست", "ارسال", "مرسوله"),
                anyKeywords = listOf(
                    "مرسوله", "تحویل", "رهگیری", "پیگیری مرسوله", "کد رهگیری", "ارسال شد",
                    "پیک", "delivery", "tracking", "shipment", "courier"
                ),
                minimumAnyMatches = 1,
                priority = 35
            ),
            FilterRule(
                categoryId = "transaction",
                displayName = "تراکنش مالی",
                senderContains = listOf("bank", "بانک", "shaparak", "payment", "card"),
                anyKeywords = listOf(
                    "تراکنش", "خرید", "پرداخت", "برداشت", "واریز", "انتقال", "کارت به کارت",
                    "موجودی", "مانده", "شماره پیگیری", "شماره مرجع", "بانک", "شاپرک",
                    "transaction", "purchase", "payment", "withdraw", "deposit", "transfer", "balance", "ref"
                ),
                minimumAnyMatches = 1,
                priority = 30
            )
        )

        private fun previousReleaseDefaultRules(): List<FilterRule> = listOf(
            FilterRule(
                categoryId = "otp",
                displayName = "کد تأیید",
                senderContains = listOf("verify", "otp", "auth", "امنیت", "تایید", "تأیید"),
                anyKeywords = listOf(
                    "رمز پویا", "رمز یکبار مصرف", "رمز یک‌بار مصرف", "کد تایید", "کد تأیید",
                    "کد ورود", "کد فعالسازی", "کد امنیتی", "verification code", "one-time", "otp", "code"
                ),
                excludedKeywords = listOf("تخفیف", "حراج", "کد درگاه"),
                minimumAnyMatches = 1,
                priority = 100
            ),
            FilterRule(
                categoryId = "promotion",
                displayName = "تبلیغاتی",
                senderContains = listOf("ads", "advert", "marketing", "promo"),
                anyKeywords = listOf(
                    "تخفیف", "تخفیفی", "حراج", "پیشنهاد ویژه", "کد تخفیف", "فروش ویژه", "جشنواره",
                    "کف خرید", "حداقل خرید", "خرید اقساطی", "اقساطی", "اعتبارت بخر", "هدیه",
                    "بسته ویژه", "بعدا بده", "بعداً بده", "آخر ماه پرداخت",
                    "discount", "sale", "offer", "promo", "promotion", "coupon", "%"
                ),
                excludedKeywords = listOf(
                    "تراکنش", "واریز مبلغ", "برداشت مبلغ", "انتقال وجه", "انتقال به",
                    "کارت به کارت", "خرید به مبلغ", "خرید با کارت", "پرداخت قسط قرارداد",
                    "پرداخت قسط", "با موفقیت پرداخت شد", "رمز پویا", "کد تأیید", "کد تایید"
                ),
                minimumAnyMatches = 1,
                priority = 60
            ),
            FilterRule(
                categoryId = "service",
                displayName = "خدمات",
                senderContains = listOf("service", "support", "notify", "سرویس", "policefata"),
                anyKeywords = listOf(
                    "فعال سازی", "فعالسازی", "غیرفعال سازی", "غیرفعالسازی", "اشتراک", "تمدید",
                    "قبض", "یادآوری", "اعلان", "درخواست", "خدمات", "اختلال", "سرویس دهی",
                    "ورود به سامانه", "وارد همراه بانک", "وارد سامانه", "اطلاع رسانی",
                    "شهروند گرامی", "کلاهبرداری", "کلاهبرداران", "هک گوشی", "سرقت موجودی",
                    "هشدار", "اخطار", "پشتیبانی",
                    "service", "subscription", "renewal", "support", "notification"
                ),
                excludedKeywords = listOf(
                    "تخفیف", "حراج", "واریز مبلغ", "برداشت مبلغ", "انتقال وجه",
                    "انتقال به", "کارت به کارت", "خرید به مبلغ", "خرید با کارت", "پرداخت قسط"
                ),
                minimumAnyMatches = 1,
                priority = 50
            ),
            FilterRule(
                categoryId = "delivery",
                displayName = "ارسال و تحویل",
                senderContains = listOf("post", "delivery", "courier", "پست", "ارسال", "مرسوله"),
                anyKeywords = listOf(
                    "مرسوله", "تحویل", "رهگیری", "پیگیری مرسوله", "کد رهگیری", "ارسال شد",
                    "پیک", "delivery", "tracking", "shipment", "courier"
                ),
                minimumAnyMatches = 1,
                priority = 35
            ),
            FilterRule(
                categoryId = "transaction",
                displayName = "تراکنش مالی",
                senderContains = listOf("bank", "بانک", "shaparak", "payment", "card"),
                anyKeywords = listOf(
                    "واریز:", "واریز مبلغ", "برداشت:", "برداشت مبلغ", "انتقال وجه", "انتقال به",
                    "کارت به کارت", "خرید به مبلغ", "خرید با کارت", "پرداخت قسط قرارداد",
                    "پرداخت قسط", "با موفقیت پرداخت شد",
                    "deposit", "withdrawal", "money transfer", "card to card"
                ),
                minimumAnyMatches = 1,
                priority = 30
            )
        )

    }
}
