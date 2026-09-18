package com.mesterumailer.smsmanager.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsBlockFilterTest {
    @Test
    fun blocksMatchingSender() {
        val filter = SmsBlockFilter(
            blockedSenders = listOf("0912", "SpamBrand"),
            blockedContent = emptyList()
        )
        assertTrue(filter.isBlocked("۰۹۱۲۱۲۳۴۵۶۷", "سلام"))
        assertTrue(filter.isBlocked("SpamBrand", "سلام"))
    }

    @Test
    fun blocksMatchingContent() {
        val filter = SmsBlockFilter(
            blockedSenders = emptyList(),
            blockedContent = listOf("لغو اشتراک", "کد تخفیف")
        )
        assertTrue(filter.isBlocked("1000", "برای لغو اشتراک عدد 1 را ارسال کنید"))
        assertTrue(filter.isBlocked("1000", "کد تخفیف شما آماده است"))
    }

    @Test
    fun senderAndContentRulesAreIndependent() {
        val filter = SmsBlockFilter(
            blockedSenders = listOf("SpamBrand"),
            blockedContent = listOf("برنده شدید")
        )
        assertTrue(filter.isBlocked("SpamBrand", "پیام عادی"))
        assertTrue(filter.isBlocked("1000", "شما برنده شدید"))
        assertFalse(filter.isBlocked("1000", "تراکنش موفق انجام شد"))
    }

    @Test
    fun normalizesPersianDigitsAndArabicLetters() {
        val filter = SmsBlockFilter(
            blockedSenders = listOf("کد۱۲۳"),
            blockedContent = listOf("يک عبارت")
        )
        assertTrue(filter.isBlocked("کد123", "سلام"))
        assertTrue(filter.isBlocked("1000", "یک عبارت"))
    }
}
