package com.mesterumailer.smsmanager.model

data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val analysis: SmsAnalysis
)

data class SmsAnalysis(
    val category: SmsCategory,
    val categoryId: String = category.id,
    val otpCode: String? = null,
    val amount: String? = null,
    val confidence: Float = 0f
)

enum class SmsCategory(val id: String, val label: String) {
    OTP("otp", "کد تأیید"),
    TRANSACTION("transaction", "تراکنش مالی"),
    DELIVERY("delivery", "ارسال و تحویل"),
    SERVICE("service", "خدمات"),
    PROMOTION("promotion", "تبلیغاتی"),
    UNKNOWN("unknown", "سایر");

    companion object {
        fun fromId(id: String): SmsCategory = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}
