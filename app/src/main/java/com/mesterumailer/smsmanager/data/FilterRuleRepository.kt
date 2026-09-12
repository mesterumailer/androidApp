package com.mesterumailer.smsmanager.data

import android.content.Context
import com.mesterumailer.smsmanager.model.FilterRule
import org.json.JSONArray
import org.json.JSONObject

class FilterRuleRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadRules(): List<FilterRule> {
        val stored = preferences.getString(KEY_RULES, null) ?: return defaultRules()
        return runCatching {
            val array = JSONArray(stored)
            buildList(array.length()) {
                for (i in 0 until array.length()) add(FilterRule.fromJson(array.getJSONObject(i)))
            }
        }.getOrElse { defaultRules() }
    }

    fun saveRules(rules: List<FilterRule>) {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_RULES, array.toString()).apply()
    }

    fun resetToDefaults() = saveRules(defaultRules())

    companion object {
        private const val PREFERENCES_NAME = "sms_manager_settings"
        private const val KEY_RULES = "filter_rules"

        fun defaultRules(): List<FilterRule> = listOf(
            FilterRule(
                categoryId = "promotion",
                displayName = "تبلیغات",
                senderContains = listOf("ads", "advert", "marketing"),
                anyKeywords = listOf(
                    "تخفیف", "حراج", "پیشنهاد ویژه", "کد تخفیف", "فروش ویژه", "جشنواره",
                    "discount", "sale", "offer", "promo", "promotion", "coupon", "%"
                ),
                excludedKeywords = listOf("تراکنش", "برداشت", "واریز", "رمز پویا", "کد تایید", "کد تأیید"),
                priority = 10
            ),
            FilterRule(
                categoryId = "service",
                displayName = "خدمات",
                senderContains = listOf("service", "support", "notify", "سرویس"),
                anyKeywords = listOf(
                    "فعال سازی", "فعالسازی", "غیرفعال سازی", "غیرفعالسازی", "اشتراک", "تمدید",
                    "قبض", "یادآوری", "اعلان", "درخواست", "پیگیری", "تحویل", "مرسوله",
                    "service", "subscription", "renewal", "support", "notification", "delivery", "tracking"
                ),
                excludedKeywords = listOf("تخفیف", "حراج", "برداشت", "واریز", "خرید", "تراکنش"),
                priority = 20
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
                requiredKeywords = emptyList(),
                excludedKeywords = emptyList(),
                priority = 30
            )
        )
    }
}
