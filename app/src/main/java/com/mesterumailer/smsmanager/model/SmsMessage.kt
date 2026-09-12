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
    val otpCode: String? = null,
    val amount: String? = null,
    val confidence: Float = 0f
)

enum class SmsCategory(val label: String) {
    OTP("کد تأیید"),
    TRANSACTION("تراکنش مالی"),
    DELIVERY("ارسال و تحویل"),
    SERVICE("خدمات"),
    PROMOTION("تبلیغاتی"),
    UNKNOWN("سایر")
}
