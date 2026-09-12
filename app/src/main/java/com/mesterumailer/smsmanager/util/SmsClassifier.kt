package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.SmsAnalysis
import com.mesterumailer.smsmanager.model.SmsCategory

class SmsClassifier {
    private val otpRegex = Regex("(?<!\\d)\\d{4,8}(?!\\d)")
    private val amountRegex = Regex("(?i)(?:amount|مبلغ|برداشت|واریز|خرید)\\D{0,20}([\\d,.]+)")

    fun analyze(body: String): SmsAnalysis {
        val text = body.lowercase()
        val otp = if (containsAny(text, "otp", "verification", "verify", "کد تایید", "کد تأیید", "رمز یکبار مصرف", "رمز پویا")) {
            otpRegex.find(body)?.value
        } else null

        val amount = amountRegex.find(body)?.groupValues?.getOrNull(1)

        return when {
            otp != null -> SmsAnalysis(SmsCategory.OTP, otpCode = otp, confidence = 0.97f)
            containsAny(text, "transaction", "purchase", "payment", "withdraw", "deposit", "card", "تراکنش", "خرید", "پرداخت", "برداشت", "واریز", "بانک", "موجودی") ->
                SmsAnalysis(SmsCategory.TRANSACTION, amount = amount, confidence = 0.92f)
            containsAny(text, "delivery", "delivered", "tracking", "shipment", "ارسال", "تحویل", "مرسوله", "پیگیری", "پست", "پیک") ->
                SmsAnalysis(SmsCategory.DELIVERY, confidence = 0.88f)
            containsAny(text, "service", "subscription", "اشتراک", "سرویس", "فعالسازی", "فعال سازی", "غیرفعال سازی") ->
                SmsAnalysis(SmsCategory.SERVICE, confidence = 0.78f)
            containsAny(text, "offer", "discount", "sale", "promo", "حراج", "تخفیف", "پیشنهاد ویژه", "باشگاه مشتریان") ->
                SmsAnalysis(SmsCategory.PROMOTION, confidence = 0.85f)
            else -> SmsAnalysis(SmsCategory.UNKNOWN)
        }
    }

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it) }
}
