package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsClassifierTest {
    private val rules = listOf(
        FilterRule(
            categoryId = "promotion",
            displayName = "تبلیغات",
            anyKeywords = listOf("تخفیف", "حراج", "کد تخفیف"),
            excludedKeywords = listOf("برداشت", "واریز", "تراکنش"),
            minimumAnyMatches = 1,
            priority = 10
        ),
        FilterRule(
            categoryId = "service",
            displayName = "خدمات",
            anyKeywords = listOf("قبض", "اشتراک", "تحویل", "پیگیری"),
            excludedKeywords = listOf("تراکنش", "برداشت", "واریز"),
            minimumAnyMatches = 1,
            priority = 20
        ),
        FilterRule(
            categoryId = "transaction",
            displayName = "تراکنش مالی",
            senderContains = listOf("bank", "بانک"),
            anyKeywords = listOf("خرید", "برداشت", "واریز", "تراکنش", "مبلغ"),
            minimumAnyMatches = 1,
            priority = 30
        )
    )

    private val classifier = SmsClassifier(rules)

    @Test
    fun classifiesPersianTransactionAndExtractsAmount() {
        val result = classifier.analyze("بانک ملت", "خرید به مبلغ 125,000 تومان با کارت انجام شد")
        assertEquals(SmsCategory.TRANSACTION, result.category)
        assertNotNull(result.amount)
    }

    @Test
    fun classifiesPromotionFromUserRule() {
        val result = classifier.analyze("90001", "فقط امروز 50% تخفیف ویژه")
        assertEquals(SmsCategory.PROMOTION, result.category)
    }

    @Test
    fun excludedKeywordPreventsPromotionMatch() {
        val result = classifier.analyze("بانک", "برداشت 500,000 تومان؛ تخفیف ندارد")
        assertEquals(SmsCategory.TRANSACTION, result.category)
    }

    @Test
    fun minimumKeywordMatchesAreRespected() {
        val strictRule = FilterRule(
            categoryId = "promotion",
            displayName = "تبلیغات",
            anyKeywords = listOf("تخفیف", "حراج"),
            minimumAnyMatches = 2
        )
        val strictClassifier = SmsClassifier(listOf(strictRule))
        val result = strictClassifier.analyze("90001", "امروز تخفیف داریم")
        assertEquals(SmsCategory.UNKNOWN, result.category)
    }

    @Test
    fun keepsUnmatchedMessageUnknown() {
        val result = classifier.analyze("دوست", "سلام، امروز ساعت 8 حرکت می‌کنیم")
        assertEquals(SmsCategory.UNKNOWN, result.category)
        assertNull(result.amount)
    }
}
