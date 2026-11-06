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
    fun customCategoriesUseTheSameWeightedEngineAndUserWeights() {
        val rules = listOf(
            FilterRule(
                categoryId = "custom_low",
                displayName = "امتیاز کم",
                anyKeywords = listOf("آزمایش"),
                keywordWeights = mapOf("آزمایش" to 2)
            ),
            FilterRule(
                categoryId = "custom_high",
                displayName = "امتیاز زیاد",
                anyKeywords = listOf("آزمایش"),
                keywordWeights = mapOf("آزمایش" to 9)
            )
        )
        val result = SmsClassifier(rules).analyze("فرستنده", "آزمایش")
        assertEquals("custom_high", result.categoryId)
    }

    @Test
    fun editingABuiltInRuleDoesNotSwitchToASeparateClassifier() {
        val modified = FilterRuleRepository.defaultRules().map { rule ->
            if (rule.categoryId == "promotion") {
                rule.copy(
                    anyKeywords = rule.anyKeywords + "عبارت آزمایشی",
                    keywordWeights = rule.keywordWeights + ("عبارت آزمایشی" to 15)
                )
            } else rule
        }
        val classifier = SmsClassifier(modified)
        val result = classifier.analyze("شماره ناشناس", "عبارت آزمایشی")
        assertEquals("promotion", result.categoryId)
        assertEquals(15, classifier.keywordWeightsFor(modified.first { it.categoryId == "promotion" })["عبارت آزمایشی"])
    }

    @Test
    fun userCanSetAnIndividualSenderWeight() {
        val rules = listOf(
            FilterRule(
                categoryId = "custom_sender_low",
                displayName = "فرستنده کم",
                senderContains = listOf("notify"),
                senderWeights = mapOf("notify" to 2)
            ),
            FilterRule(
                categoryId = "custom_sender_high",
                displayName = "فرستنده زیاد",
                senderContains = listOf("notify"),
                senderWeights = mapOf("notify" to 12)
            )
        )
        val result = SmsClassifier(rules).analyze("notify-service", "پیام")
        assertEquals("custom_sender_high", result.categoryId)
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
            if (actual != expectedCategory) throw AssertionError("Reported SMS #${index + 1}; expected=$expectedCategory; actual=$actual; sender=$sender; body=$body")
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

    @Test
    fun classifiesRecentMisclassifiedDeliveryAndUnknownSamples() {
        val samples = listOf(
            Triple("+989998624166", "نوین ارت البرز تست و تحویل همبندی الکتریکی تاییدیه ارت مشاوره تخصصی اجرای تخصصی استعلام قیمت", "promotion"),
            Triple("SNAPPFOOD", "سفارش شما از حلیم و آش و سوپ گل گندم، توسط پیک بردار به زودی به شما تحویل داده می‌شود. رهگیری سفارش", "delivery"),
            Triple("IRAN POST", "پستچی ظرف روز جاری برای توزیع مرسوله 594860521800306833165630 مراجعه می نماید", "delivery"),
            Triple("+989125550016", "سلام مهندس جان، خرما را از حراست قدیم تحویل گرفتید؟", "unknown"),
            Triple("MALIAT", "مؤدی گرامی سازمان امور مالیاتی کشور مالیات عملکرد سال 1404 پرونده شما با شماره رهگیری تعیین شده است و نیازی به مراجعه حضوری نیست", "service"),
            Triple("+9810004347", "پامین‌مارکت سفارش شما تحویل پست شد کدرهگیری مرسوله پستی 594860521800306833165630 رهگیری پست", "delivery"),
            Triple("IRAN POST", "مرسوله 594860521800306833165630 از شهر تبریز قبول و به نشانی شما ارسال خواهد گردید", "delivery"),
            Triple("IRAN POST", "مرسوله پستی شما با موفقیت ثبت گردید جهت مشاهده جزئیات به اپلیکیشن پست من مراجعه نمایید tracking.post.ir", "delivery"),
            Triple("+989125550016", "لطفاً خرما را از درب حراست قدیم شرکت داخل قفسه ها تحویل بگیرید", "unknown"),
            Triple("9830005090000104", "راهکار قبولی در مرحله مصاحبه کلاس صفر تا صد کل مباحث فاضل", "promotion"),
            Triple("+989982008609", "DJ Night رستوران مریم پنجشنبه و جمعه رزرو میز", "promotion"),
            Triple("+9890008102", "سفارش شماره 3673737 در وضعیت انجام شده قرار گرفت کافه ارز", "service"),
            Triple("TejaratBank", "خرید مجموعه کافه ارز مبلغ 7,124,200 رمز 212437 تاریخ 1405/08/13", "transaction"),
            Triple("TejaratBank", "خرید مجموعه کافه ارز مبلغ 7,124,200 رمز 424811 تاریخ 1405/08/13", "transaction"),
            Triple("9830009417", "بانک ملی ایران قسط:1,120,770- حساب:01007 مانده:500,000", "transaction"),
            Triple("+98500036000313", "کنسرت مسیح و آرش 26 آبان هتل اسپیناس تهیه بلیت", "promotion"),
            Triple("9830009417", "بانک ملی ایران انتقال:1,600,000+ حساب:01007 مانده:1,620,770", "transaction"),
            Triple("+985000310327", "فروشگاه لونا تویز دیجی کالا با شرکت در نظرسنجی جامع آبان ماه به ما کمک می کنید تجربه همکاری را بهتر کنیم", "service"),
            Triple("+989998763502", "کسب و کارت چیه؟ به مشتریای مرتبط پیامک بزن", "promotion"),
            Triple("+98100050055505", "تو فقط بسته هات رو آماده کن جمع آوری با الوپست رایگان ازت می گیریم و به سراسر ایران می فرستیم", "promotion"),
            Triple("+98500032153924", "همراه عزیز دیجی پی با ثبت تجربه استفاده از سرویس ها در لینک زیر راهنمای ساخت تجربه های بهتر باش", "service"),
            Triple("9830005090508651", "قبولی مرحله مهم مصاحبه کارشناس رسمی باکلاس صفر تا صد", "promotion"),
            Triple("+985000361930015", "این بار نوبت شنیدن از شماست همکار عزیز در نظرسنجی جامع آبان ماه شرکت کنید و تجربه ها و پیشنهادها را بگویید", "service"),
            Triple("+989982008534", "ثالث بدنه 10 قسط ویژه مهندس شریفی فر", "promotion"),
            Triple("+9890008102", "سفارش شماره 3670603 به علت عدم پرداخت کنسل شد کافه ارز", "service"),
            Triple("+985000333", "برای ساختن آینده روشن با همایون شجریان و دیجی کالا مهر در کمپین هرجا که تویی همراه شو جایزه لپ تاپ", "promotion"),
            Triple("+989982008534", "شریفی فر ثبت نام تکمیلی بیمه لینک ثبت نام", "service"),
            Triple("+9890006541", "برای بهتر شدن پرتال شنیدن تجربه شما برای ما مهم است اگر هنوز در نظرسنجی شرکت نکرده اید", "service"),
            Triple("+989998624166", "برای سئو یک ابزار کافیه کیوردچی هم کیورد ریسرچ داره هم لینک واچ و ابزار GEO و تولید محتوای سئو شده", "promotion"),
            Triple("+985000361930015", "صدای شما مسیر رشد ما همکار عزیز برای بهتر شدن مسیر همکاری در نظرسنجی جامع آبان ماه شرکت کنید", "service"),
            Triple("TejaratBank", "تراکنش حسابی مبدا 2**137 مقصد 0139249211955 مبلغ 11,000,000 رمز 701224", "transaction"),
            Triple("E.S.police", "ستاد خبری پلیس امنیت اقتصادی فراجا هموطن گرامی برای مقابله با مجرمین اخبار خود را ارسال نمایید", "service"),
            Triple("+982170002198", "ثبت نام رایگان تلفن ابری ویژه فروشگاه اینترنتی", "promotion"),
            Triple("BaIrancell", "تا جمعه به 3 گیگابایت مصرف اینترنت برس و 1 گیگابایت اینترنت 3 روزه رایگان دریافت کن هدیه", "promotion"),
            Triple("+989982008534", "بیمه البرز درمان مهندسان ثبت نام", "promotion"),
            Triple("TejaratBank", "خرید شیراز سوشال مبلغ 276,450 رمز 474548 تاریخ 1405/08/10", "transaction"),
            Triple("983000941001", "بانک ملی خرید شیراز سوشال مبلغ 276,450 رمز 6672508", "transaction"),
            Triple("MCI Academy", "بهترین معلم زندگیت رو انتخاب کن جایزه معلم رویدادی برای شناسایی آموزگاران ایرانی معرفی کن", "promotion"),
            Triple("+989173311497", "فدات نوش", "unknown"),
            Triple("+9890007827", "مهندس گرامی خلاصه رایگان کتاب رهبری انرژی", "promotion"),
            Triple("+9810004347", "به تیکت شماره 150232 شما پاسخ داده شد کافه ارز", "service"),
            Triple("TejaratBank", "خرید دیجی کالا مبلغ 2,970,000 رمز 604087 تاریخ 1405/08/08", "transaction")
        )

        samples.forEachIndexed { index, (sender, body, expectedCategory) ->
            val actual = defaultClassifier.analyze(sender, body).categoryId
            if (actual != expectedCategory) throw AssertionError("Latest batch sample #${index + 1}; expected=$expectedCategory; actual=$actual; sender=$sender; body=$body")
        }
    }

    @Test
    fun genericDeliveryWordsDoNotCauseFalsePositive() {
        val samples = listOf(
            "+989125550016" to "خرما را از حراست قدیم تحویل گرفتید؟",
            "MALIAT" to "پرونده مالیاتی با شماره رهگیری 3213995638 تعیین شد",
            "+989998624166" to "تست و تحویل، اجرای تخصصی و استعلام قیمت"
        )
        samples.forEach { (sender, body) ->
            assertEquals(false, defaultClassifier.analyze(sender, body).categoryId == "delivery")
        }
    }

    @Test
    fun bankAccountBalanceAloneDoesNotMakeFinancialTransaction() {
        assertEquals("unknown", defaultClassifier.analyze("دوست", "مانده حساب شما 500,000 ریال است").categoryId)
    }


    @Test
    fun transferInstructionsWithoutAmountAreNotFinancialTransactions() {
        val result = defaultClassifier.analyze(
            "TejaratBank",
            "برای انتقال وجه از منوی انتقال حساب استفاده کنید"
        )
        assertEquals("unknown", result.categoryId)
    }

    @Test
    fun explicitBankOtpBeatsGenericLoginServicePhrases() {
        val result = defaultClassifier.analyze(
            "Bank Melli",
            "بانک ملی کد 82989 را جهت ورود به سامانه بام وارد نمایید. QVcOXy7hhZr"
        )
        assertEquals("otp", result.categoryId)
        assertEquals("82989", result.otpCode)
    }

    @Test
    fun bankTransactionWithAmountAndTransactionWordingIsRecognized() {
        val result = defaultClassifier.analyze(
            "TejaratBank",
            "تراکنش حسابی مبدا 2**137 مقصد 0139249211955 مبلغ 11,000,000 رمز 701224"
        )
        assertEquals("transaction", result.categoryId)
    }

    @Test
    fun installmentReminderWithoutSuccessfulPaymentIsNotTransaction() {
        val result = defaultClassifier.analyze(
            "TejaratBank",
            "یادآوری: پرداخت قسط قرارداد تا تاریخ 1405/09/01 انجام شود"
        )
        assertEquals(false, result.categoryId == "transaction")
    }

}
