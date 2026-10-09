package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun higherPriorityRuleWinsWhenBothRulesMatch() {
        val transactionRule = FilterRule(
            categoryId = SmsCategory.TRANSACTION.id,
            displayName = "تراکنش مالی",
            anyKeywords = listOf("خرید", "مبلغ", "تراکنش"),
            minimumAnyMatches = 1,
            priority = 30
        )
        val otpRule = FilterRule(
            categoryId = SmsCategory.OTP.id,
            displayName = "کد تأیید",
            anyKeywords = listOf("کد تایید"),
            minimumAnyMatches = 1,
            priority = 50
        )

        val result = SmsClassifier(listOf(transactionRule, otpRule))
            .analyze("بانک ملت", "خرید به مبلغ 100000 انجام شد. کد تایید: 483921")

        assertEquals(SmsCategory.OTP, result.category)
    }

    @Test
    fun bankOtpMessageUsesOtpCategoryEvenWhenTransactionKeywordsArePresent() {
        val result = SmsClassifier(FilterRuleRepository.defaultRules())
            .analyze(
                "بانک ملت",
                "خرید به مبلغ 100000 تومان انجام شد. کد تایید: 483921"
            )

        assertEquals(SmsCategory.OTP, result.category)
        assertEquals("483921", result.otpCode)
    }

    @Test
    fun bankDynamicPasswordMessageUsesOtpCategory() {
        val result = SmsClassifier(FilterRuleRepository.defaultRules())
            .analyze(
                "بانک ملت",
                "برای تایید تراکنش، رمز: 7314 را وارد کنید."
            )

        assertEquals(SmsCategory.OTP, result.category)
        assertEquals("7314", result.otpCode)
    }

    @Test
    fun otpContextCanMatchEvenWithoutAnExplicitOtpKeyword() {
        val otpRule = FilterRule(
            categoryId = SmsCategory.OTP.id,
            displayName = "کد تأیید",
            anyKeywords = listOf("کد تایید"),
            minimumAnyMatches = 1,
            priority = 50
        )

        val result = SmsClassifier(listOf(otpRule))
            .analyze("بانک ملت", "رمز: 7314")

        assertEquals(SmsCategory.OTP, result.category)
        assertEquals("7314", result.otpCode)
    }

    @Test
    fun suggestedKeywordsCanMatchSenderName() {
        val senderKeywordRule = FilterRule(
            categoryId = SmsCategory.DELIVERY.id,
            displayName = "ارسال و تحویل",
            anyKeywords = listOf("courier"),
            minimumAnyMatches = 1,
            priority = 35
        )

        val result = SmsClassifier(listOf(senderKeywordRule))
            .analyze("Courier Services", "سفارش شما ثبت شد")

        assertEquals(SmsCategory.DELIVERY, result.category)
    }

    @Test
    fun builtInDefaultRulesHaveNoSenderContainsConstraints() {
        assertTrue(FilterRuleRepository.defaultRules().all { it.senderContains.isEmpty() })
    }

    @Test
    fun migratesOnlyLegacyDefaultSenderListsAndPreservesCustomizedFilters() {
        val legacyTransaction = FilterRule(
            categoryId = "transaction",
            displayName = "تراکنش مالی",
            senderContains = listOf("bank", "بانک", "shaparak", "payment", "card"),
            anyKeywords = listOf("خرید")
        )
        val customized = FilterRule(
            categoryId = "custom_bank",
            displayName = "بانک دلخواه",
            senderContains = listOf("TrustedSender"),
            anyKeywords = listOf("خرید")
        )

        val migrated = FilterRuleRepository.migrateLegacyDefaultSenderFilters(
            listOf(legacyTransaction, customized)
        )

        assertTrue(migrated[0].senderContains.isEmpty())
        assertEquals(listOf("TrustedSender"), migrated[1].senderContains)
    }

    @Test
    fun otpSenderNameAloneDoesNotMakeOrdinaryMessageAnOtp() {
        val result = SmsClassifier(FilterRuleRepository.defaultRules())
            .analyze("Code Service", "حساب شما به‌روزرسانی شد")

        assertTrue(result.category != SmsCategory.OTP)
        assertNull(result.otpCode)
    }

    @Test
    fun classifiesPersianTransactionWithoutAmountExtraction() {
        val result = classifier.analyze("بانک ملت", "خرید به مبلغ 125,000 تومان با کارت انجام شد")
        assertEquals(SmsCategory.TRANSACTION, result.category)
        assertNull(result.amount)
    }

    @Test
    fun senderFilterRejectsTextualSenderMismatchEvenWhenKeywordMatches() {
        val senderRule = FilterRule(
            categoryId = "transaction",
            displayName = "تراکنش مالی",
            senderContains = listOf("bank", "بانک"),
            anyKeywords = listOf("خرید"),
            minimumAnyMatches = 1,
            priority = 30
        )

        val result = SmsClassifier(listOf(senderRule))
            .analyze("فروشگاه", "خرید با کارت انجام شد")

        assertEquals(SmsCategory.UNKNOWN, result.category)
    }

    @Test
    fun senderFilterRejectsNumericSenderMismatchEvenWhenKeywordMatches() {
        val senderRule = FilterRule(
            categoryId = "transaction",
            displayName = "تراکنش مالی",
            senderContains = listOf("bank", "بانک"),
            anyKeywords = listOf("خرید"),
            minimumAnyMatches = 1,
            priority = 30
        )

        val result = SmsClassifier(listOf(senderRule))
            .analyze("50001234", "خرید با کارت انجام شد")

        assertEquals(SmsCategory.UNKNOWN, result.category)
    }

    @Test
    fun senderFilterMatchesConfiguredSender() {
        val senderRule = FilterRule(
            categoryId = "transaction",
            displayName = "تراکنش مالی",
            senderContains = listOf("bank", "بانک"),
            anyKeywords = listOf("خرید"),
            minimumAnyMatches = 1,
            priority = 30
        )

        val result = SmsClassifier(listOf(senderRule))
            .analyze("بانک ملت", "خرید با کارت انجام شد")

        assertEquals(SmsCategory.TRANSACTION, result.category)
    }

    @Test
    fun allRequiredKeywordsMustMatch() {
        val strictRule = FilterRule(
            categoryId = "service",
            displayName = "خدمات",
            requiredKeywords = listOf("بانک", "کارت"),
            anyKeywords = listOf("فعال"),
            minimumAnyMatches = 1
        )

        val result = SmsClassifier(listOf(strictRule))
            .analyze("بانک ملت", "کارت فعال شد")

        assertEquals(SmsCategory.UNKNOWN, result.category)
    }

    @Test
    fun requiredKeywordsMatchWhenAllArePresent() {
        val strictRule = FilterRule(
            categoryId = "service",
            displayName = "خدمات",
            requiredKeywords = listOf("بانک", "کارت"),
            anyKeywords = listOf("فعال"),
            minimumAnyMatches = 1
        )

        val result = SmsClassifier(listOf(strictRule))
            .analyze("بانک ملت", "بانک اعلام کرد کارت فعال شد")

        assertEquals(SmsCategory.SERVICE, result.category)
    }


    @Test
    fun extractsOtpAfterPersianConfirmationCue() {
        val result = classifier.analyze("سرویس", "کد تایید: 483921")

        assertEquals("483921", result.otpCode)
    }

    @Test
    fun extractsOtpAfterPersianConfirmationCueWithoutColon() {
        val result = classifier.analyze("سرویس", "کد تایید شما 483921")

        assertEquals("483921", result.otpCode)
    }

    @Test
    fun extractsOtpAfterPersianPasswordCueWithPersianDigits() {
        val result = classifier.analyze("سرویس", "رمز پویا: ۷۳۱۴")

        assertEquals("7314", result.otpCode)
    }

    @Test
    fun extractsOtpAfterEnglishCodeCue() {
        val result = classifier.analyze("Service", "Verification Code: 483921")

        assertEquals("483921", result.otpCode)
    }

    @Test
    fun extractsOtpAfterEnglishOtpCue() {
        val result = classifier.analyze("Service", "OTP 483921")

        assertEquals("483921", result.otpCode)
    }

    @Test
    fun doesNotExtractUnrelatedNumberWithoutOtpContext() {
        val result = classifier.analyze("Service", "شماره پیگیری: 78123456")

        assertNull(result.otpCode)
    }

    @Test
    fun doesNotTreatGenericColonAsOtpContext() {
        val result = classifier.analyze("Service", "مبلغ: 450000")

        assertNull(result.otpCode)
    }

    @Test
    fun rejectsTrackingCodeEvenThoughItFollowsTheWordCode() {
        val result = classifier.analyze("Service", "کد رهگیری: 78123456")

        assertNull(result.otpCode)
    }

    @Test
    fun rejectsDiscountCodeFromOtpExtraction() {
        val result = classifier.analyze("Service", "کد تخفیف: 483921")

        assertNull(result.otpCode)
    }

    @Test
    fun skipsRejectedNumberAndFindsNearbyOtpCode() {
        val result = classifier.analyze(
            "Service",
            "کد رهگیری: 78123456، سپس کد تایید: 483921"
        )

        assertEquals("483921", result.otpCode)
    }

    @Test
    fun doesNotExtractNumberWhenOtpCueIsTooFarAway() {
        val result = classifier.analyze(
            "Service",
            "کد تایید برای شما صادر شد و این متن عمداً بیشتر از فاصله مجاز ادامه پیدا می‌کند تا عدد در انتها قرار بگیرد 483921"
        )

        assertNull(result.otpCode)
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
    fun preservesCustomCategoryIdForUserDefinedRule() {
        val customRule = FilterRule(
            categoryId = "custom_bank_cards",
            displayName = "کارت‌ها",
            anyKeywords = listOf("کارت ویژه"),
            minimumAnyMatches = 1
        )
        val result = SmsClassifier(listOf(customRule)).analyze("سرویس", "کارت ویژه شما آماده است")

        assertEquals("custom_bank_cards", result.categoryId)
        assertEquals(SmsCategory.UNKNOWN, result.category)
    }

    @Test
    fun keepsUnmatchedMessageUnknown() {
        val result = classifier.analyze("دوست", "سلام، امروز ساعت 8 حرکت می‌کنیم")
        assertEquals(SmsCategory.UNKNOWN, result.category)
        assertNull(result.amount)
    }
}
