# SMS Manager for Android

مدیریت و تحلیل پیامک‌های دریافتی روی Android، با تمرکز فعلی روی **فاز ۱: Inbox، دسته‌بندی قابل تنظیم و استخراج اطلاعات مهم**.

> وضعیت فعلی: نسخه `0.2.0` — MVP قابل تست

## هدف پروژه

این پروژه در نسخه‌های اولیه قرار نیست جایگزین برنامه پیام‌رسان پیش‌فرض Android شود. تمرکز فعلی این است که بدون Default SMS App شدن:

- پیامک‌های دریافتی موجود در Inbox را بخوانیم.
- پیامک‌ها را نمایش و تحلیل کنیم.
- سه دسته اولیه **تبلیغات، خدمات و تراکنش مالی** را با قواعد قابل تنظیم مدیریت کنیم.
- قواعد دسته‌بندی hard-code نباشند و کاربر بتواند آن‌ها را تغییر دهد.
- اطلاعات مهم مثل OTP و مبلغ تراکنش را استخراج کنیم.

## فاز ۱ — پیاده‌سازی فعلی

### قابلیت‌های موجود

- درخواست runtime permission برای `READ_SMS`.
- خواندن پیامک‌های Inbox از `Telephony.Sms.Inbox`.
- نمایش حداکثر ۲۰۰ پیام اخیر.
- منوی همبرگری برای دسترسی به Settings.
- صفحه Settings برای ویرایش قواعد دسته‌بندی.
- ذخیره محلی تنظیمات در `SharedPreferences`.
- سه Rule پیش‌فرض:
  - **تبلیغات**
  - **خدمات**
  - **تراکنش مالی**
- هر Rule دارای این فیلترهاست:
  - عبارات فرستنده
  - کلمات الزامی
  - کلمات تشخیصی
  - کلمات ممنوع
  - حداقل تعداد کلمات تشخیصی
  - اولویت Rule
- تطبیق Ruleها با نرمال‌سازی فارسی/انگلیسی و تبدیل اعداد فارسی/عربی به انگلیسی.
- حل هم‌پوشانی Ruleها با امتیازدهی و priority.
- بازخوانی خودکار Inbox بعد از بازگشت از Settings.
- استخراج اولیه کدهای ۴ تا ۸ رقمی.
- استخراج اولیه مبلغ تراکنش.
- تست واحد برای matching و exclusion و threshold.

### منطق Rule

یک پیام برای یک دسته زمانی match می‌شود که:

1. هیچ عبارت ممنوع آن Rule در متن نباشد.
2. تمام کلمات الزامی وجود داشته باشند.
3. حداقل تعداد مشخصی از کلمات تشخیصی پیدا شوند.
4. در صورت تعریف sender filter، وجود نشانه فرستنده به امتیاز Rule کمک می‌کند.
5. در صورت match چند Rule، Rule دارای امتیاز بالاتر انتخاب می‌شود و priority نقش tie-breaker دارد.

به این ترتیب تغییر فیلتر در Settings واقعاً موتور دسته‌بندی را تغییر می‌دهد.

## فیلترهای اولیه

مقادیر اولیه فقط نقطه شروع هستند و کاربر می‌تواند آن‌ها را تغییر دهد.

### تبلیغات

نشانه‌های اولیه شامل تخفیف، حراج، پیشنهاد ویژه، کد تخفیف، فروش ویژه، جشنواره، discount، sale، offer، promo و coupon است. برخی نشانه‌های مالی و OTP نیز برای جلوگیری از اشتباه در این دسته excluded شده‌اند.

### خدمات

نشانه‌های اولیه شامل اشتراک، تمدید، قبض، یادآوری، اعلان، درخواست، تحویل، مرسوله، پیگیری، service، support، subscription، renewal، delivery و tracking است. نشانه‌های مالی/تبلیغاتی اصلی excluded شده‌اند.

### تراکنش مالی

نشانه‌های اولیه شامل تراکنش، خرید، پرداخت، برداشت، واریز، انتقال، کارت به کارت، موجودی، مانده، شماره پیگیری، شماره مرجع، بانک، شاپرک، transaction، purchase، payment، withdraw، deposit، transfer و balance است.

## معماری فعلی

```text
MainActivity
   |
   +--> FilterRuleRepository ---> SharedPreferences
   |
   +--> SmsRepository
             |
             +--> Telephony.Sms.Inbox
             |
             +--> SmsClassifier(rules)
                       |
                       +--> SmsAnalysis
                              +--> Category
                              +--> OTP
                              +--> Amount

MainActivity
   |
   +--> Hamburger Drawer
             |
             +--> SettingsActivity
                       |
                       +--> Edit Rule
                       +--> Save / Reset defaults
```

## ساختار پروژه

```text
.
├── app/
│   ├── src/main/AndroidManifest.xml
│   ├── src/main/java/com/mesterumailer/smsmanager/
│   │   ├── MainActivity.kt
│   │   ├── SettingsActivity.kt
│   │   ├── data/
│   │   │   ├── FilterRuleRepository.kt
│   │   │   └── SmsRepository.kt
│   │   ├── model/
│   │   │   ├── FilterRule.kt
│   │   │   └── SmsMessage.kt
│   │   └── util/SmsClassifier.kt
│   └── src/test/java/com/mesterumailer/smsmanager/
│       └── util/SmsClassifierTest.kt
├── .github/workflows/android.yml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

## نیازمندی‌ها

- Android Studio با Android SDK مناسب پروژه
- JDK 17
- Android API 35 برای compile/target
- حداقل Android API 26

## اجرای پروژه

۱. repository را clone کنید.

۲. پروژه را در Android Studio باز کنید و Gradle sync را انجام دهید.

۳. برنامه را روی دستگاه Android اجرا کنید.

۴. مجوز خواندن SMS را تأیید کنید.

۵. از منوی همبرگری وارد **تنظیمات دسته‌بندی** شوید.

۶. فیلترهای سه دسته را تغییر دهید و ذخیره کنید.

۷. به Inbox برگردید؛ پیام‌ها با Ruleهای جدید دوباره تحلیل می‌شوند.

## تست خودکار با GitHub Actions

Workflow پروژه در `.github/workflows/android.yml` قرار دارد و برای push روی `main` و branchهای `feature/**` و همچنین Pull Request اجرا می‌شود.

Workflow این موارد را بررسی می‌کند:

- Checkout
- JDK 17
- Gradle
- `testDebugUnitTest`
- `assembleDebug`
- تولید APK
- upload کردن APK به‌عنوان artifact

آخرین build موفق فاز قبلی با همین pipeline ثبت شده و artifact با نام `sms-manager-debug-apk` تولید شده است.

## امنیت و حریم خصوصی

- در فاز فعلی فقط `READ_SMS` درخواست می‌شود.
- متن پیامک در log چاپ نمی‌شود.
- پیامک‌ها در دیتابیس جداگانه ذخیره نمی‌شوند.
- Ruleهای کاربر فقط به‌صورت تنظیمات محلی نگهداری می‌شوند.
- هیچ قابلیت ارسال، حذف یا تغییر SMS در این فاز وجود ندارد.

## خارج از فاز فعلی

- Default SMS App
- ارسال SMS
- حذف یا تغییر SMS
- دریافت زنده SMS با BroadcastReceiver
- همگام‌سازی ابری
- حساب کاربری
- ML/LLM classification
- workflowهای پیچیده کاربر

## مسیر توسعه بعدی

### Phase 1.1 — Inbox حرفه‌ای‌تر

- جستجو
- فیلتر سریع دسته‌ها
- مرتب‌سازی
- فقط پیام‌های مهم
- نمایش confidence
- ویرایش Rule مستقیماً از کارت پیام
- تست Rule با یک پیام نمونه قبل از ذخیره

### Phase 1.2 — دریافت پیام جدید

- `RECEIVE_SMS`
- `BroadcastReceiver`
- پردازش لحظه‌ای
- جلوگیری از duplicate processing

### Phase 1.3 — persistence

- Room در صورت نیاز
- نگهداری تحلیل‌ها
- history و statistics

### Phase 2 — تحلیل هوشمند

- استخراج entityهای بیشتر
- تشخیص دقیق فرستنده/سرویس
- تشخیص نوع تراکنش
- scoring پیشرفته
- امکان اتصال classifier هوشمند

## وضعیت توسعه

- [x] ایجاد پروژه Android
- [x] خواندن Inbox
- [x] نمایش پیامک‌ها
- [x] منوی همبرگری
- [x] صفحه Settings
- [x] Ruleهای قابل تنظیم
- [x] ذخیره محلی Ruleها
- [x] سه دسته اولیه تبلیغات/خدمات/تراکنش مالی
- [x] threshold و exclusion برای Ruleها
- [x] تست واحد matching
- [x] GitHub Actions برای build و test
- [ ] جستجو و فیلتر UI
- [ ] دریافت زنده SMS
- [ ] persistence
- [ ] تحلیل پیشرفته

## Git workflow

توسعه قابلیت‌ها روی branchهای feature انجام می‌شود و `main` برای نسخه‌های پایدار نگه داشته می‌شود.

Branch فعلی:

```text
feature/phase-1-sms-inbox
```
