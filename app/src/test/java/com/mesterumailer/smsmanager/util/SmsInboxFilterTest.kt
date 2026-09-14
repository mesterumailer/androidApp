package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.SmsAnalysis
import com.mesterumailer.smsmanager.model.SmsCategory
import com.mesterumailer.smsmanager.model.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsInboxFilterTest {
    private val transaction = message(1, "بانک ملت", "خرید به مبلغ 120000 تومان")
    private val promotion = message(2, "فروشگاه", "تخفیف ویژه امروز")
    private val service = message(3, "شرکت", "مرسوله شما تحویل شد")
    private val messages = listOf(transaction, promotion, service)

    @Test
    fun findsPartOfMessageBody() {
        val result = SmsInboxFilter.filter(messages, SmsCategory.entries.toSet(), "120000")
        assertEquals(listOf(transaction), result)
    }

    @Test
    fun findsSenderText() {
        val result = SmsInboxFilter.filter(messages, SmsCategory.entries.toSet(), "بانک")
        assertEquals(listOf(transaction), result)
    }

    @Test
    fun combinesSearchWithCategoryVisibility() {
        val result = SmsInboxFilter.filter(messages, setOf(SmsCategory.SERVICE), "مرسوله")
        assertEquals(listOf(service), result)
    }

    @Test
    fun normalizesPersianAndArabicVariants() {
        assertTrue(SmsInboxFilter.normalize("كی‌م") .contains("کیم"))
        assertEquals("۱۲۳۴۵", "۱۲۳۴۵")
        assertEquals("12345", SmsInboxFilter.normalize("۱۲۳۴۵"))
    }

    private fun message(id: Long, address: String, body: String) = SmsMessage(
        id = id,
        address = address,
        body = body,
        timestamp = id,
        analysis = SmsAnalysis(category = when (id) {
            1L -> SmsCategory.TRANSACTION
            2L -> SmsCategory.PROMOTION
            else -> SmsCategory.SERVICE
        })
    )
}
