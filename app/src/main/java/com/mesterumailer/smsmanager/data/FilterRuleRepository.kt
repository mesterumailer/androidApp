package com.mesterumailer.smsmanager.data

import android.content.Context
import com.mesterumailer.smsmanager.model.FilterRule
import org.json.JSONArray

class FilterRuleRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadRules(): List<FilterRule> {
        val stored = preferences.getString(KEY_RULES, null) ?: return defaultRules()
        return runCatching {
            val array = JSONArray(stored)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    add(FilterRule.fromJson(array.getJSONObject(i)))
                }
            }.filter { it.categoryId.isNotBlank() && it.displayName.isNotBlank() }
        }.getOrElse { defaultRules() }
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

    fun resetToDefaults() = saveRules(defaultRules())

    companion object {
        private const val PREFERENCES_NAME = "sms_manager_settings"
        private const val KEY_RULES = "filter_rules"

        fun defaultRules(): List<FilterRule> = listOf(
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
    }
}
