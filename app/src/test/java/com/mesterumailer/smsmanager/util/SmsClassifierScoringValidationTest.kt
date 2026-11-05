package com.mesterumailer.smsmanager.util

import com.mesterumailer.smsmanager.data.FilterRuleRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Labelled validation corpus from real user-reported "Other" messages.
 * Expected labels were assigned by message intent, not by the old classifier.
 */
class SmsClassifierScoringValidationTest {
    private val classifier = SmsClassifier(FilterRuleRepository.defaultRules())

    private data class Sample(val id: Int, val sender: String, val body: String, val expected: String)

    private val samples = listOf(
        Sample(1, "+98500032153924", "نظرت برامون مهمه همراه عزیز دیجی پی با ثبت تجربه استفاده از سرویس ها راهنمای ساخت تجربه های بهتر باش لغو11", "service"),
        Sample(2, "TIPAX", "سلام لطفا موقعیت گیرنده بسته با کد 74241202611182371125 را از لینک زیر انتخاب کنید تیپاکس", "delivery"),
        Sample(3, "+989173311497", "فدات نوش", "unknown"),
        Sample(4, "+989173311497", "سلام احمدرضا جان آقا قابل شما را نداره قیمت مجموعا میشه ۹۱۰۰۰۰ ش کارت 6037997264461410 سپاس", "unknown"),
        Sample(5, "+9890008102", "کارت بانکی 5859831179914923 در وضعیت تایید قرار گرفت کافه ارز لغو 11", "service"),
        Sample(6, "+9890008102", "کارت بانکی به شماره 5859831179914923 در وضعیت در حال بررسی قرار گرفت کافه ارز", "service"),
        Sample(7, "98200090070", "ورود به سایت در تاریخ 1405/08/08 تهران پیمنت", "service"),
        Sample(8, "98200090070", "کد بازیابی رمز عبور شما در تهران پیمنت : 936080", "otp"),
        Sample(9, "Snapp", "با اسنپ میوه تازه بخر برای سفارش میوه تازه کافیه توی سرویس غذای اسنپ دسته بندی میوه را انتخاب کنی", "promotion"),
        Sample(10, "98200090070", "کد بازیابی رمز عبور شما در تهران پیمنت : 908422", "otp"),
        Sample(11, "+985000333", "۱۰۰ هزار تومان طلای دیجیتال بعد از خرید پلاس برات فعال شده و فقط ۲۴ ساعت فرصت استفاده داری کد pt0j9pwfid9qqxt25mwy", "promotion"),
        Sample(12, "Snapp", "با اسنپ میوه تازه بخر برای سفارش میوه تازه کافیه دسته بندی میوه را انتخاب کنی", "promotion"),
        Sample(13, "9830003103", "فیلم راهنمای مشتری هنگام ترخیص خودرو با نصب سامانه همراه ایساکو مرکز رضایت و خشنودی مشتریان ایران خودرو", "service"),
        Sample(14, "MCI Club", "هر روز می تونی شانست رو امتحان کنی و امتیاز باشگاه برنده بشی همین الان شانست رو امتحان کن", "promotion"),
        Sample(15, "DK Sellers", "آخرین روز بازارگان در روز پایانی به سراغ پرسش و پاسخ درباره تغییرات مالی پنل فروشندگان می رویم نشست فرصت خوبی است", "promotion"),
        Sample(16, "Snapp", "صبح با یه صبحونه دلچسب از صبحونه انگلیسی و حلیم تا املت و صبحونه ایرانی رو با سرویس غذای اسنپ سفارش بده", "promotion"),
        Sample(17, "B.QMEHRIRAN", "پرداخت قرض الحسنه مهر ایران 2,437,000 ريال رمز 061921 اعتبار 21:11:49", "transaction"),
        Sample(18, "B.QMEHRIRAN", "پرداخت قرض الحسنه مهر ایران 24,503,140 ريال رمز 152263 اعتبار 21:09:41", "transaction"),
        Sample(19, "B.QMEHRIRAN", "پرداخت قرض الحسنه مهر ایران 40,026,293 ريال رمز 002072 اعتبار 21:08:38", "transaction"),
        Sample(20, "+9850003004602", "پرداخت بدهی و شارژ اعتبار دیجی پی اعتبار قابل مصرف: ۶۸۸٬۵۱۷٬۲۸۰ ریال", "service"),
        Sample(21, "AZKIVAM", "۳۰۰م وام بانکی ویژه خرید کالا ازکی وام", "promotion"),
        Sample(22, "+98500030001125", "برای خرید میز و صندلی به پس اندازت دست نزن با سلام پی ۱۲ ماهه پرداخت کن دریافت وام", "promotion"),
        Sample(23, "TejaratBank", "بانک تجارت حساب واریز حقوق : 573,932,000 ریال از طریق شعبه مانده: 576,240,039 ریال", "transaction"),
        Sample(24, "+985000310327", "از ارسال توسط فروشنده استفاده می کنید این نشست برای شماست نشست تخصصی ارسال توسط فروشنده ارسال ۳ ساعته و دیجی اکسپرس هم اکنون در رویداد بازارگان آغاز شده است همین حالا به پخش زنده بپیوندید", "promotion"),
        Sample(25, "DK Sellers", "از ارسال توسط فروشنده استفاده می کنید این نشست برای شماست نشست تخصصی ارسال توسط فروشنده ارسال ۳ ساعته و دیجی اکسپرس هم اکنون در رویداد بازارگان آغاز شده است همین حالا به پخش زنده بپیوندید", "promotion")
    )

    @Test
    fun classifiesAllTwentyFiveReportedMessages() {
        samples.forEach { sample ->
            assertEquals(
                "SMS #${sample.id} sender=${sample.sender}",
                sample.expected,
                classifier.analyze(sample.sender, sample.body).categoryId
            )
        }
    }

    /**
     * Fixed holdout slice (IDs chosen before running the validation):
     * covers private text, sender-only service context, OTP, delivery,
     * payment-with-OTP, balance notice and event promotion.
     */
    @Test
    fun holdoutSlicePassesWithoutChangingExpectedLabels() {
        val holdoutIds = setOf(2, 3, 5, 7, 8, 17, 20, 24)
        val holdout = samples.filter { it.id in holdoutIds }
        assertEquals(8, holdout.size)
        holdout.forEach { sample ->
            assertEquals(
                "HOLDOUT SMS #${sample.id}",
                sample.expected,
                classifier.analyze(sample.sender, sample.body).categoryId
            )
        }
    }

    @Test
    fun senderEvidenceIsWeightedButCannotOverrideContradictoryIntent() {
        val senderStrong = SmsClassifier(FilterRuleRepository.defaultRules(), senderWeight = 2)
        val result = senderStrong.analyze("TejaratBank", "کد بازیابی رمز عبور شما : 123456")
        assertEquals("otp", result.categoryId)
    }
}
