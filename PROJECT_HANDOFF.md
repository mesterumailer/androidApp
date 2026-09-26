# Project Handoff / Agent Continuation Guide

این فایل مرجع ادامه توسعه پروژه برای توسعه‌دهنده انسانی و Agentهای هوش مصنوعی است. هدف آن این است که یک Agent جدید بدون اتکا به حافظه گفتگوهای قبلی بتواند وضعیت واقعی پروژه، تصمیم‌های قطعی، معماری فعلی، محدودیت‌ها و مسیر ادامه توسعه را درک کند.

> **آخرین ممیزی مستندات و کد:** 2026-09-26  
> **مرجع وضعیت فعلی:** branch `feature/phase-1-sms-inbox`، PR #3 به `main`

---

## 1. شناسنامه پروژه

- Repository: `mesterumailer/androidApp`
- نام پروژه Android: `SmsManager`
- Application ID: `com.mesterumailer.smsmanager`
- Namespace: `com.mesterumailer.smsmanager`
- زبان: Kotlin
- UI: Android Views / programmatic UI؛ پروژه فعلی Jetpack Compose نیست.
- compileSdk: 35
- targetSdk: 35
- minSdk: 26
- Java/Kotlin target: 17
- Android Gradle Plugin: 8.7.3
- Kotlin plugin: 2.0.21
- Gradle مورد استفاده در CI: 8.9
- نسخه فعلی اپ در کد: `0.7.0`
- versionCode فعلی: `12`

---

## 2. ایده محصول

محصول یک SMS Manager برای Android است که در فازهای اولیه قرار نیست جایگزین برنامه پیام‌رسان پیش‌فرض Android شود.

هدف فعلی این است که بدون Default SMS App شدن:

1. پیامک‌های دریافتی موجود در Inbox خوانده شوند.
2. پیامک‌ها در یک Inbox ساده و کاربردی نمایش داده شوند.
3. پیامک‌ها با Ruleهای قابل تنظیم دسته‌بندی شوند.
4. اطلاعات مهم اولیه مثل OTP و مبلغ تراکنش استخراج شود.
5. کاربر بتواند روی نمایش دسته‌ها، جست‌وجو، فیلتر مسدودسازی و اعلان‌ها کنترل داشته باشد.
6. دریافت پیامک جدید به‌صورت زنده پردازش شود و در صورت فعال بودن اعلان، Notification مناسب تولید شود.

### خارج از محدوده فاز فعلی

- Default SMS App شدن
- ارسال SMS
- حذف SMS
- تغییر SMS سیستم
- Cloud Sync
- حساب کاربری
- ML/LLM classification
- ذخیره دائمی تحلیل همه پیامک‌ها در دیتابیس مستقل

این مرزبندی یک تصمیم محصولی قطعی برای نسخه‌های اولیه است و نباید در توسعه فعلی به‌صورت پیش‌فرض شکسته شود.

---

## 3. وضعیت واقعی فعلی

### انجام‌شده

- پروژه Android ساخته شده است.
- Permissionهای `READ_SMS` و `RECEIVE_SMS` در Manifest وجود دارند.
- برای Android 13+ مجوز `POST_NOTIFICATIONS` نیز در Manifest وجود دارد.
- Inbox از `Telephony.Sms.Inbox` خوانده می‌شود.
- سقف خواندن Inbox قابل تنظیم است.
- مقدار پیش‌فرض سقف Inbox: **1000 پیام**
- حداقل: **200**
- حداکثر: **5000**
- دو حالت محدوده خواندن:
  - آخرین پیام‌ها
  - تا تاریخ مشخص
- دو ترتیب فعلی نمایش:
  - جدیدتر به قدیمی‌تر
  - قدیمی‌تر به جدیدتر
- UI فارسی و RTL است.
- تم روشن و تاریک وجود دارد و انتخاب کاربر در `SharedPreferences` ذخیره می‌شود.
- صفحه اصلی دارای جست‌وجوی زنده در متن پیام و نام/شماره فرستنده است.
- فیلتر نمایش دسته‌ها در یک بخش مستقل از صفحه اصلی وجود دارد.
- فیلتر دسته و Search با هم ترکیب می‌شوند.
- دسته‌ها دارای وضعیت فعال/غیرفعال هستند.
- فعال/غیرفعال بودن دسته در `SharedPreferences` ذخیره می‌شود.
- Block Filter بر اساس sender و content وجود دارد.
- پیام مسدودشده در برنامه وارد Classification/Notification نمی‌شود.
- SMS اصلی سیستم با Block Filter حذف نمی‌شود.
- Ruleهای تشخیص قابل ویرایش هستند.
- Ruleها شامل senderContains، requiredKeywords، anyKeywords، excludedKeywords، minimumAnyMatches و priority هستند.
- نرمال‌سازی متن برای فارسی/انگلیسی و اعداد فارسی/عربی انجام می‌شود.
- هم‌پوشانی Ruleها با score و priority حل می‌شود.
- دسته‌بندی دستی پیام وجود دارد.
- Override دسته‌بندی پیام در `SharedPreferences` ذخیره می‌شود.
- امکان بازگشت از Override به تشخیص خودکار وجود دارد.
- OTP چهار تا هشت رقمی به‌صورت اولیه استخراج می‌شود.
- مبلغ تراکنش به‌صورت اولیه استخراج می‌شود.
- SMS چندقسمتی در `SmsReceiver` از طریق `Telephony.Sms.Intents.getMessagesFromIntent()` به متن واحد تبدیل می‌شود.
- duplicate notification با نگهداری کلیدهای اخیر در `SharedPreferences` کنترل می‌شود.
- Notification به تفکیک category طراحی شده است.
- برای هر category تنظیم Enable/Disable مستقل وجود دارد.
- انتخاب صدای هر category از Ringtone Picker سیستم وجود دارد.
- پیش‌فرض صدای categoryها بدون صدا است.
- ویبره برنامه عمداً فعال نمی‌شود.
- اجرای تست واحد برای matching/filter/policy و بخش‌های کمکی وجود دارد.
- GitHub Actions برای debug build + unit test وجود دارد.
- Workflow جداگانه برای release signed APK وجود دارد.

### هنوز کامل نشده

- Persistence واقعی تحلیل‌ها با Room یا دیتابیس مشابه
- History / statistics
- تحلیل پیشرفته‌تر
- confidence قابل نمایش به کاربر
- مرتب‌سازی پیشرفته‌تر از newest/oldest
- فیلتر «فقط پیام‌های مهم»
- ویرایش Rule مستقیماً از کارت پیام
- تست Rule با پیام نمونه قبل از ذخیره
- classifier هوشمندتر
- استخراج entityهای بیشتر

---

## 4. Improvement Phase

از این نقطه پروژه وارد چرخه بهبود محصول شده است: بازخورد واقعی → بازتولید → Root Cause → تغییر حداقلی و ایمن → Regression Test → Device Test در موارد لازم → به‌روزرسانی مستندات → Verify.

Baseline رفتارهای فعلی در `REGRESSION_BASELINE.md` و بازخوردهای مشاهده‌شده در `PRODUCT_FEEDBACK.md` ثبت می‌شوند. این دو فایل برای جلوگیری از regression باید همراه تغییرات مهم به‌روز بمانند.

قاعده این فاز: قابلیت‌های فعلی تا حد امکان باید بدون تغییر رفتاری حفظ شوند. قبل از هر refactor یا feature بزرگ، اثر آن بر baseline بررسی شود.

## 5. Known Issues فعلی

### 4.1 صدای Notification

در تست عملی روی گوشی گزارش شده که با وجود انتخاب صدای اختصاصی برای یک category، هنگام رسیدن SMS و نمایش Notification صدایی پخش نشده است.

کد فعلی از Android Notification Channel استفاده می‌کند:

- Channel ID به‌صورت `sms_category_<categoryId>` است.
- هنگام انتخاب صدا، `SmsNotificationManager.recreateChannel()` اجرا می‌شود.
- Channel با `NotificationManager.IMPORTANCE_DEFAULT` در صورت داشتن صدا ساخته می‌شود.
- `setSound(soundUri, AudioAttributes...)` روی Channel اعمال می‌شود.
- ویبره روی Channel غیرفعال است.

**این مورد هنوز Root Cause قطعی ندارد. GitHub Issue آن: [#4](https://github.com/mesterumailer/androidApp/issues/4).**

عامل‌های احتمالی که باید در ادامه بررسی شوند:

1. رفتار Notification Channel در نسخه Android/ROM دستگاه آزمایشی
2. وضعیت Channel در تنظیمات سیستم Android
3. سطح اهمیت Notification و mute شدن Channel توسط سیستم یا کاربر
4. اعتبار/قابلیت دسترسی URI انتخاب‌شده
5. Volume مسیر Notification در دستگاه
6. تفاوت رفتار دستگاه‌های مختلف
7. lifecycle و delete/recreate شدن Channel
8. اینکه Channel قدیمی با state متفاوت در سیستم باقی مانده باشد

این مسئله باید به‌عنوان Bug واقعی در نظر گرفته شود و قبل از اعلام پایداری کامل Notification رفع و روی دستگاه واقعی دوباره تست شود.

---

## 6. مدل دسته‌بندی فعلی

Enum اصلی:

```text
OTP          -> otp           -> کد تأیید
TRANSACTION  -> transaction   -> تراکنش مالی
DELIVERY     -> delivery      -> ارسال و تحویل
SERVICE      -> service       -> خدمات
PROMOTION    -> promotion     -> تبلیغاتی
UNKNOWN      -> unknown       -> سایر
```

نکته مهم: Rule سفارشی می‌تواند `categoryId` جدید داشته باشد. در این حالت `SmsAnalysis.categoryId` همان ID سفارشی را حفظ می‌کند، ولی `SmsAnalysis.category` در Enum به `UNKNOWN` برمی‌گردد.

---

## 7. Rule Engine

مدل `FilterRule` دارای این فیلدها است:

```text
categoryId
displayName
senderContains
requiredKeywords
anyKeywords
excludedKeywords
minimumAnyMatches
priority
```

### منطق فعلی score

به‌صورت مفهومی:

```text
any keyword hit  -> +2 برای هر مورد
sender hit       -> +4 برای هر مورد
required keyword -> +3 برای هر مورد
priority         -> priority / 10
```

Rule در شرایط زیر رد می‌شود:

1. یکی از excludedKeywords در متن باشد.
2. تعداد anyKeywords کمتر از minimumAnyMatches باشد.
3. یکی از requiredKeywords موجود نباشد.
4. اگر senderContains تعریف شده باشد و نه sender match داشته باشیم و نه any keyword match، Rule رد می‌شود.

در صورت چند candidate، ابتدا score بالاتر و سپس priority بالاتر انتخاب می‌شود.

### Ruleهای پیش‌فرض

Ruleهای پیش‌فرض در `FilterRuleRepository.defaultRules()` تعریف شده‌اند.

Categoryهای اولیه:

- OTP
- Promotion
- Service
- Delivery
- Transaction

دسته `UNKNOWN` یک دسته سیستمی fallback است و Rule قابل ویرایش جداگانه ندارد.

---

## 8. مسیر پردازش Inbox

```text
Android Telephony.Sms.Inbox
          |
          v
     SmsRepository
          |
          v
      Block Filter
          |
          v
      SmsClassifier
          |
          +----> category
          +----> categoryId
          +----> OTP
          +----> amount
          +----> confidence
          |
          v
   MessageOverrideRepository
          |
          v
   Category Activation / Visibility
          |
          v
       Search Filter
          |
          v
        Sort
          |
          v
       Inbox UI
```

نکته مهم: Block Filter قبل از Classification اعمال می‌شود.

پیام مسدودشده:

- در Inbox برنامه نمایش داده نمی‌شود.
- برای Classification استفاده نمی‌شود.
- برای Notification استفاده نمی‌شود.
- از SMSهای سیستم Android حذف نمی‌شود.

---

## 9. مسیر دریافت SMS زنده

Receiver:

```text
SmsReceiver
```

Action:

```text
android.provider.Telephony.SMS_RECEIVED
```

مراحل:

1. Intent بررسی می‌شود.
2. پیام‌ها با `Telephony.Sms.Intents.getMessagesFromIntent()` دریافت می‌شوند.
3. قطعات پیام به body واحد تبدیل می‌شوند.
4. sender و timestamp استخراج می‌شوند.
5. blank body کنار گذاشته می‌شود.
6. کلید duplicate ساخته می‌شود.
7. duplicateهای اخیر رد می‌شوند.
8. Block Filter اجرا می‌شود.
9. Ruleها load می‌شوند.
10. `SmsClassifier` تحلیل را انجام می‌دهد.
11. `SmsNotificationSettingsRepository` تنظیم category را می‌خواند.
12. `SmsNotificationPolicy` بررسی می‌کند که Notification مجاز است یا نه.
13. `SmsNotificationManager.notifyIncoming()` Notification را می‌سازد.

### نکته مهم درباره Category Activation

در طراحی فعلی، فعال/غیرفعال بودن category برای نمایش Inbox با فعال بودن Notification یکی نیست.

همچنین Notification routing عمداً مستقل از Category Visibility/Activation صفحه Inbox پیاده شده است.

بنابراین:

- مخفی‌کردن category در Inbox لزوماً Notification آن را خاموش نمی‌کند.
- فعال بودن Notification category توسط `SmsNotificationSettingsRepository` تعیین می‌شود.

این رفتار فعلی است و قبل از تغییر آن باید تصمیم محصولی جداگانه گرفته شود.

---

## 10. Notification Architecture

فایل اصلی:

```text
app/src/main/java/com/mesterumailer/smsmanager/notification/SmsNotificationManager.kt
```

Channel ID:

```text
sms_category_<categoryId>
```

برای Android O+:

- Channel بر اساس category ساخته می‌شود.
- اگر sound وجود داشته باشد Importance برابر `IMPORTANCE_DEFAULT` است.
- اگر sound تهی باشد Importance برابر `IMPORTANCE_LOW` است.
- vibration غیرفعال است.
- audio usage برابر `USAGE_NOTIFICATION` است.
- content type برابر `CONTENT_TYPE_SONIFICATION` است.
- badge فعال است.

برای Android قدیمی‌تر از O:

- sound مستقیم روی Notification Builder تنظیم می‌شود.
- vibration نیز غیرفعال است.

Android 13+:

- قبل از ارسال Notification مجوز `POST_NOTIFICATIONS` بررسی می‌شود.
- اگر مجوز وجود نداشته باشد، Notification ارسال نمی‌شود.

---

## 11. تنظیمات محلی و SharedPreferences

پروژه فعلی دیتابیس Room ندارد. State محلی با `SharedPreferences` نگهداری می‌شود.

### Theme

کلاس:

```text
ThemePreferenceRepository
```

Preferences:

```text
sms_manager_theme_preferences
```

Key:

```text
theme
```

### Category visibility

کلاس:

```text
CategoryVisibilityRepository
```

Preferences:

```text
category_visibility
```

Key pattern:

```text
visible_<categoryId>
```

### Category activation

کلاس:

```text
CategoryActivationRepository
```

Preferences:

```text
category_activation
```

Key pattern:

```text
active_<categoryId>
```

### Rules

کلاس:

```text
FilterRuleRepository
```

Preferences:

```text
sms_manager_settings
```

Key:

```text
filter_rules
```

Ruleها به JSON ذخیره می‌شوند.

### Block filters

کلاس:

```text
SmsBlockRepository
```

Preferences:

```text
sms_block_filters
```

Keyها:

```text
blocked_senders
blocked_content
```

### Notification settings

کلاس:

```text
SmsNotificationSettingsRepository
```

Preferences:

```text
sms_notification_settings
```

Keyها:

```text
enabled_<categoryId>
sound_<categoryId>
```

### Message overrides

کلاس:

```text
MessageOverrideRepository
```

Preferences:

```text
message_overrides
```

کلید هر message بر اساس SMS ID ذخیره می‌شود.

### Inbox settings

کلاس:

```text
SmsSettingsRepository
```

Preferences:

```text
sms_settings
```

Keyهای اصلی:

```text
inbox_limit
inbox_read_mode
inbox_date_start
inbox_sort_order
```

---

## 12. تنظیمات Inbox

ثابت‌های فعلی:

```text
DEFAULT_LIMIT = 1000
MIN_LIMIT     = 200
MAX_LIMIT     = 5000
```

حالت‌های فعلی:

```text
LATEST_MESSAGES
UNTIL_DATE
```

ترتیب‌های فعلی:

```text
NEWEST_FIRST
OLDEST_FIRST
```

`LATEST_MESSAGES` یعنی از جدیدترین پیام‌ها شروع می‌شود.

`UNTIL_DATE` شرط timestamp را روی پیام‌های قدیمی‌تر از زمان پایان روز بعد از تاریخ انتخاب‌شده اعمال می‌کند.

---

## 13. جست‌وجو و فیلتر نمایش

فایل:

```text
SmsInboxFilter.kt
```

Search روی:

1. body
2. address

اعمال می‌شود.

Normalization شامل موارد زیر است:

- lowercase
- تبدیل بعضی حروف عربی به فارسی
- تبدیل اعداد فارسی به انگلیسی
- حذف نیم‌فاصله برای Search
- trim

فیلتر category نیز قبل از نمایش اعمال می‌شود.

ترتیب کلی UI:

```text
source Inbox
 -> category visibility
 -> search
 -> sort
 -> render
```

---

## 14. Override دسته‌بندی

کاربر می‌تواند category یک پیام را به‌صورت دستی تغییر دهد.

این تغییر:

- متن واقعی SMS را تغییر نمی‌دهد.
- SMS سیستم را تغییر نمی‌دهد.
- در `MessageOverrideRepository` ذخیره می‌شود.
- امکان clear کردن override دارد.

پیام پس از clear شدن override دوباره از classification خودکار استفاده می‌کند.

---

## 15. Permissions

Manifest فعلی:

```xml
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Receiver با permission:

```text
android.permission.BROADCAST_SMS
```

تعریف شده است.

### Permission flow

```text
PermissionSetupActivity
        |
        +--> READ_SMS
        +--> RECEIVE_SMS
        |
        v
MainActivity
```

Notification permission در Android 13+ فقط وقتی لازم باشد درخواست می‌شود.

---

## 16. ساختار سورس

```text
app/src/main/java/com/mesterumailer/smsmanager/
|
├── BaseActivity.kt
├── MainActivity.kt
├── SettingsActivity.kt
├── AppSettingsActivity.kt
├── PermissionSetupActivity.kt
├── SmsReceiver.kt
├── AppThemeManager.kt
|
├── data/
│   ├── CategoryActivationRepository.kt
│   ├── CategoryVisibilityRepository.kt
│   ├── FilterRuleRepository.kt
│   ├── MessageOverrideRepository.kt
│   ├── SmsBlockRepository.kt
│   ├── SmsNotificationSettingsRepository.kt
│   ├── SmsRepository.kt
│   ├── SmsSettingsRepository.kt
│   └── ThemePreferenceRepository.kt
|
├── model/
│   ├── FilterRule.kt
│   └── SmsMessage.kt
|
├── notification/
│   └── SmsNotificationManager.kt
|
└── util/
    ├── SmsBlockFilter.kt
    ├── SmsClassifier.kt
    ├── SmsInboxFilter.kt
    ├── SmsNotificationPolicy.kt
    └── SmsTextProcessor.kt
```

---

## 17. تست‌ها

تست‌های فعلی در:

```text
app/src/test/java/com/mesterumailer/smsmanager/
```

کلاس‌های موجود:

- `AppThemeTest`
- `InboxReadModeTest`
- `SmsBlockFilterTest`
- `SmsClassifierTest`
- `SmsInboxFilterTest`
- `SmsNotificationPolicyTest`
- `SmsTextProcessorTest`

تمرکز فعلی Unit Testها:

- classification
- exclusion
- threshold
- custom category ID
- search
- category filter
- normalization
- block filters
- notification policy
- theme parsing
- inbox read mode parsing
- URL detection

### نکته

Unit Test نمی‌تواند جای تست واقعی SMS receiver و Notification Channel روی دستگاه را بگیرد.

برای Notification و SMS باید تست دستگاه واقعی نیز انجام شود.

---

## 18. تست دستی مرجع

برای نسخه فعلی این سناریوها باید تست شوند:

1. نصب APK.
2. دادن READ_SMS و RECEIVE_SMS.
3. نمایش پیام‌های موجود Inbox.
4. فعال کردن Notification یک category.
5. دریافت SMS جدید همان category.
6. بررسی عنوان/متن/category Notification.
7. انتخاب sound و بررسی پخش واقعی آن.
8. انتخاب بدون صدا و بررسی silent بودن.
9. بررسی نبود vibration.
10. خاموش‌کردن Notification category.
11. Block کردن sender.
12. Block کردن content phrase.
13. بررسی عدم Notification پیام blocked.
14. لمس Notification و باز شدن MainActivity در context پیام.
15. دریافت SMS چندقسمتی.
16. ارسال/دریافت event تکراری و بررسی duplicate prevention.
17. تغییر Rule و بازگشت به Inbox.
18. تغییر دسته دستی و سپس clear override.
19. تست search روی sender.
20. تست search روی body.
21. تست هم‌زمان search + category filter.
22. تست newest/oldest sort.
23. تست limit مختلف Inbox.
24. تست until-date.
25. تست light/dark theme.

---

## 19. CI / GitHub Actions

### Debug workflow

فایل:

```text
.github/workflows/android.yml
```

Trigger:

- pull_request
- push روی `main`
- push روی `feature/**`

مراحل:

```text
checkout
-> JDK 17
-> Gradle 8.9
-> testDebugUnitTest
-> assembleDebug
-> upload debug APK
```

### Release workflow

فایل:

```text
.github/workflows/android-release.yml
```

Trigger:

- workflow_dispatch
- push روی `feature/phase-1-sms-inbox`
- tagهای `v*`

Release signing با GitHub Secrets انجام می‌شود.

Secrets:

```text
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Keystore نباید وارد repository شود.

---

## 20. Git وضعیت فعلی

Branch اصلی توسعه:

```text
feature/phase-1-sms-inbox
```

Branch پایدار:

```text
main
```

PR فعلی:

```text
#3
feat: phase 1 SMS inbox manager
```

وضعیت PR در آخرین ممیزی:

- open
- draft
- merged: false
- mergeable: false

مقایسه فعلی:

```text
main
    ^ merge base: ad85ee3d9bd2575686237402169b0c36760df458
    |
feature/phase-1-sms-inbox
```

در آخرین بررسی:

- feature branch: **230 commit جلوتر**
- feature branch: **2 commit عقب‌تر**
- وضعیت: **diverged**

دو commit موجود در `main` و خارج از feature branch:

1. `6192159ec...` — `Add configurable SMS inbox limit`
2. `beb20585a...` — `fix: make Android Release workflow manually runnable from default branch`

بنابراین قبل از merge کردن feature branch باید divergence با دقت بررسی و conflictهای احتمالی حل شوند.

**نباید صرفاً با force push یا بازنویسی history مشکل را حل کرد مگر اینکه تصمیم صریح و آگاهانه گرفته شود.**

---

## 21. سیاست پیشنهادی Git برای ادامه

مدل توسعه:

```text
main
  |
  +-- feature/phase-1-sms-inbox
  |
  +-- بعداً feature/phase-2-...
```

قواعد:

- `main` برای وضعیت پایدار نگه داشته شود.
- قابلیت جدید روی feature branch توسعه یابد.
- bugfix مهم نیز ترجیحاً در branch مشخص و قابل ردیابی انجام شود.
- قبل از merge، test/build و تست دستگاه واقعی انجام شود.
- پس از کامل شدن یک milestone، feature branch با `main` همگام و سپس merge شود.
- پس از merge پایدار Phase 1، branch فاز بعدی از `main` جدید ساخته شود.
- مستندات باید هم‌زمان با تغییر معماری/رفتار واقعی به‌روز شوند.

---

## 22. ترتیب ادامه توسعه پیشنهادی

### اولویت 1 — رفع Notification Sound Bug

قبل از توسعه featureهای بزرگ، این مورد بررسی شود:

- inspect کردن Channel واقعی روی دستگاه
- بررسی importance
- بررسی sound URI
- بررسی system notification settings
- بررسی delete/recreate lifecycle
- تست روی حداقل دو Android/ROM متفاوت در صورت امکان
- افزودن تست/لاگ فنی بدون ثبت متن SMS خصوصی

هدف این است که بین «sound انتخاب شده در برنامه» و «sound واقعاً پخش‌شده توسط Android» قرارداد واضح ایجاد شود.

### اولویت 2 — پایدارسازی Phase 1

- تکمیل Notification edge cases
- تکمیل manual device test
- تست performance Inbox در limitهای بالا
- بررسی lifecycle receiver
- بررسی پاکسازی duplicate keys
- بررسی behavior بعد از reboot
- بررسی behavior بعد از تغییر Ruleها

### اولویت 3 — Inbox polish

- important-only filter
- confidence display
- sorting پیشرفته
- Rule test with sample message
- edit Rule از message card

### اولویت 4 — Persistence

در صورت نیاز:

```text
Room
  -> SMS metadata
  -> analysis
  -> category history
  -> statistics
```

قبل از اضافه کردن Room باید مشخص شود دقیقاً چه داده‌ای ارزش persistence دارد؛ صرفاً برای بزرگ‌تر کردن معماری اضافه نشود.

### اولویت 5 — Smart Analysis

بعد از پایدار شدن Rule engine:

- entity extraction
- sender/service detection
- transaction type
- better scoring
- improved classifier
- در مراحل بعد امکان classifier هوشمند

---

## 23. اصول مهندسی پروژه

Agent بعدی باید این اصول را حفظ کند:

1. **اول رفتار فعلی را بفهم، بعد کد را تغییر بده.**
2. تصمیم‌های محصولی قطعی را بدون دلیل نقض نکن.
3. SMS سیستم را در این فاز حذف/تغییر نده.
4. اطلاعات خصوصی SMS را داخل log ثبت نکن.
5. برای هر feature جدید ترجیحاً یک واحد منطقی مستقل داشته باش.
6. Repositoryها مسئول persistence/settings باشند و UI مستقیم منطق storage را تکرار نکند.
7. Classification تا حد ممکن deterministic و قابل تست بماند.
8. normalization مشترک و سازگار حفظ شود.
9. تغییرات notification را روی Android O+ با Channel semantics بررسی کن.
10. Unit Test به‌تنهایی کافی نیست؛ برای permission/receiver/notification تست دستگاه لازم است.
11. هر تغییر رفتاری مهم باید در README و همین handoff document منعکس شود.
12. قبل از merge، branch state و divergence با `main` بررسی شود.
13. از dependency جدید بدون نیاز واقعی استفاده نشود؛ هدف پروژه سبک و کم‌حجم ماندن است.
14. API level و compatibility با minSdk 26 حفظ شود مگر تصمیم محصولی جدید گرفته شود.

---

## 24. فایل‌هایی که Agent معمولاً باید ابتدا بخواند

برای هر task جدید، ترتیب پیشنهادی:

```text
1. PROJECT_HANDOFF.md
2. README.md
3. AndroidManifest.xml
4. MainActivity.kt
5. SmsReceiver.kt
6. SmsRepository.kt
7. SmsClassifier.kt
8. FilterRuleRepository.kt
9. SmsNotificationManager.kt
10. repository مرتبط با feature موردنظر
11. تست همان feature
```

برای تغییرات UI:

```text
MainActivity.kt
SettingsActivity.kt
AppSettingsActivity.kt
BaseActivity.kt
AppThemeManager.kt
res/values/*
res/values-night/*
```

برای تغییرات notification:

```text
SmsReceiver.kt
SmsNotificationManager.kt
SmsNotificationSettingsRepository.kt
SmsNotificationPolicy.kt
AppSettingsActivity.kt
SmsNotificationPolicyTest.kt
```

برای تغییرات classification:

```text
FilterRule.kt
FilterRuleRepository.kt
SmsClassifier.kt
SmsClassifierTest.kt
SmsInboxFilter.kt
```

---

## 25. قرارداد توسعه برای Agent جدید

وقتی یک Agent جدید وارد پروژه شد، بدون نیاز به پرسیدن سؤالات پایه باید از این فرض‌ها شروع کند:

- پروژه یک SMS management app است، نه SMS replacement app.
- فاز فعلی Phase 1 است.
- Default SMS role فعلاً خارج از scope است.
- داده فعلی از Android Telephony provider خوانده می‌شود.
- persistence فعلی SharedPreferences است.
- Room هنوز اضافه نشده است.
- classification فعلی rule-based و deterministic است.
- Notification بر اساس category و Notification Channel انجام می‌شود.
- کاربر در تست واقعی وجود sound bug را گزارش کرده است.
- branch توسعه فعلی `feature/phase-1-sms-inbox` است.
- branch با `main` diverged است؛ merge نباید بدون reconcile انجام شود.
- نسخه فعلی کد `0.7.0` است.
- سقف پیش‌فرض بررسی Inbox برابر 1000 پیام است.
- UI فارسی/RTL است.
- پروژه باید تا حد ممکن سبک و ساده باقی بماند.

### اصل مهم

هر Agent باید **قبل از اجرای یک تغییر بزرگ، وضعیت واقعی branch و کد را دوباره از repository بخواند**؛ این فایل راهنمای continuation است، نه جایگزین source of truth.

Source of truth به ترتیب:

```text
1. actual source code
2. tests
3. workflow configuration
4. README.md
5. PROJECT_HANDOFF.md
```

هرگاه این فایل با کد واقعی اختلاف داشت، **کد واقعی و تست‌ها مقدم هستند** و این فایل باید اصلاح شود.

---

## 26. Release readiness فعلی

در وضعیت فعلی پروژه را نباید صرفاً بر اساس وجود workflow یا Unit Test «کاملاً آماده Release» فرض کرد.

دلیل اصلی:

- تست Notification روی دستگاه واقعی یک ایراد sound را نشان داده است.
- PR فعلی هنوز draft و mergeable=false است.
- feature branch با main diverged است.
- persistence و بخش‌هایی از Phase 1 هنوز کامل نیستند.

بنابراین وضعیت فعلی را باید این‌طور در نظر گرفت:

```text
Phase 1
  -> functional prototype / active development
  -> practical testing in progress
  -> not final production baseline yet
```

---

## 27. هدف نزدیک

هدف نزدیک پروژه این نیست که فوراً قابلیت‌های زیادی اضافه شود.

هدف نزدیک:

```text
پایدار کردن هسته Phase 1
        +
رفع ایرادهای تست واقعی
        +
مستندسازی دقیق
        +
تست قابل تکرار
        +
سپس توسعه قابلیت بعدی
```

هر feature جدید باید بعد از پایدار شدن بخش‌های مرتبط وارد شود.

---

## 28. آخرین وضعیت ثبت‌شده

در آخرین وضعیت ثبت‌شده توسط تیم:

> «تست عملی انجام شده و عملکرد کلی بهتر شده است؛ با این حال هنوز کارهای زیادی باقی مانده. یکی از باگ‌های مشاهده‌شده این است که با وجود امکان تنظیم صدای category، هنگام دریافت SMS و نمایش Notification صدایی پخش نشده است.»

این گزارش باید در ادامه کار به‌عنوان ورودی معتبر QA تلقی شود تا وقتی که با تست دستگاه و اصلاح کد بسته شود.

