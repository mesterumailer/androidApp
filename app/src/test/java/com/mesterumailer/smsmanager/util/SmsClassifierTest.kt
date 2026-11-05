package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.data.FilterRuleRepository
import com.mesterumailer.smsmanager.model.FilterRule
import com.mesterumailer.smsmanager.model.SmsCategory
import org.junit.Assert.assertEquals
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
    private val defaultClassifier = SmsClassifier(FilterRuleRepository.defaultRules())

    @Test
    fun classifiesPersianTransactionWithoutAmountExtraction() {
        val result = classifier.analyze("بانک ملت", "خرید به مبلغ 125,000 تومان با کارت انجام شد")
        assertEquals(SmsCategory.TRANSACTION, result.category)
        assertNull(result.amount)
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

    @Test
    fun classifiesAllFifteenReportedMessagesByTheirPurpose() {
        val samples = listOf(
            Triple("DIGIPAY", "۱ میلیون تخفیف خرید از داروکده 💊 مکمل، پوست و مو، آرایشی بهداشتی کد درگاه دیجی‌پی: CEDRKQ کف خرید: ۵ م", "promotion"),
            Triple("PoliceFATA", "شما دعوتید به عروسی کلاهبرداران! شهروند گرامی؛ شگرد جدید کلاهبرداران سایبری ارسال برنامک های جعلی اندرویدی با عنوان کارت دعوت عروسی است؛ باز کردن لینک برابر با هک گوشی و سرقت موجودی حساب های شماست.", "service"),
            Triple("ResalatBank", "مشتری گرامی؛ به‌منظور ارتقای سطح خدمات بانکی، ممکن است موقتا سرویس‌دهی برخی از سامانه‌های این بانک با اختلال مواجه شود.", "service"),
            Triple("Bank Melli", "بانک ملی ورود به سامانه بام ۱۴۰۵/۰۸/۱۴-۱۳:۰۸", "service"),
            Triple("Bank Melli", "بانک ملی کد 82989 را جهت ورود به سامانه بام وارد نمایید. QVcOXy7hhZr", "otp"),
            Triple("+9890008136", "خرید اقساطی طلا از روبی با انتخاب روش پرداخت دلخواه شما از طریق دیجی‌پی، تارا، بلوبانک و ازکی‌وام", "promotion"),
            Triple("986715000", "بانک سپه واریز:2,385,000 ریال حساب:382650114900191 مانده:54,009,252 انتقال وجه پایا", "transaction"),
            Triple("+989999984080", "پرداخت الکترونیک سامان واریز مبلغ 40,000,000 به کارت 9804-***-621986 مانده 43,827,823", "transaction"),
            Triple("TechnoLife", "۱ میلیون تخفیف کد WERT کف خرید ۱۰ میلیون + ۱۷ گرم طلا هدیه تولد تکنولایف خرید اقساطی", "promotion"),
            Triple("+98100041415001", "پرداخت قسط قرارداد به شماره 1035013066231 با موفقیت پرداخت شد پرداخت قسط قرارداد از طریق حساب های ضامن انجام شده است", "transaction"),
            Triple("TejaratBank", "انتقال به 603769*9282 مبلغ 15,000,000 رمز 201061 تاریخ 1405/08/13", "transaction"),
            Triple("TejaratBank", "مشتری گرامی شما در۱۴۰۵/۰۸/۱۳ ساعت۲۰:۴۲وارد همراه بانک تجارت شده اید.", "service"),
            Triple("DIGIPAY", "۳ میلیون تومان تخفیف خرید انواع کتونی و پوشاک کد تخفیف CETHMC حداقل خرید: ۱۰ میلیون قابل استفاده در درگاه دیجی‌پی", "promotion"),
            Triple("IrancellNow", "بسته مخصوص خودت رو بخر، پولش رو بعداً بده! می‌تونی بسته ویژه و تخفیفی رو فعال کنی و هزینه‌ش رو آخر ماه پرداخت کنی.", "promotion"),
            Triple("TechnoLife", "تا ۶ میلیون تخفیف اپل! کد APPLE تعداد محدود + هدیه تولد تکنوگلد پرداخت اقساطی", "promotion")
        )

        samples.forEachIndexed { index, (sender, body, expectedCategory) ->
            val actual = defaultClassifier.analyze(sender, body).categoryId
            assertEquals("Reported SMS #${index + 1}", expectedCategory, actual)
        }
    }

    @Test
    fun bankSenderAloneDoesNotMakeLoginOrMaintenanceSmsFinancial() {
        val login = defaultClassifier.analyze("TejaratBank", "شما وارد همراه بانک تجارت شده اید.")
        val maintenance = defaultClassifier.analyze("ResalatBank", "سرویس دهی سامانه بانک موقتا با اختلال مواجه می شود.")

        assertEquals("service", login.categoryId)
        assertEquals("service", maintenance.categoryId)
    }

    @Test
    fun transactionPasswordDoesNotOverrideTransferClassification() {
        val result = defaultClassifier.analyze(
            "TejaratBank",
            "انتقال به 603769*9282 مبلغ 15,000,000 رمز 201061"
        )

        assertEquals("transaction", result.categoryId)
    }
}
