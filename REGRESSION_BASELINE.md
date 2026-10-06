# Regression Baseline

این فایل رفتارهایی را مشخص می‌کند که در مرحله بهبود محصول باید حفظ شوند. هر تغییر جدید باید قبل و بعد از خود، این baseline را در نظر بگیرد.

## Core behavior

- [ ] خواندن SMSهای موجود از `Telephony.Sms.Inbox`
- [ ] پیش‌فرض Inbox برابر 500 پیام
- [ ] حداقل limit برابر 200 و حداکثر 5000
- [ ] انتخاب محدوده «آخرین پیام‌ها» یا «تا تاریخ مشخص»
- [ ] ترتیب newest/oldest
- [ ] جست‌وجو در body
- [ ] جست‌وجو در sender/address
- [ ] مسدودسازی فرستنده از کارت پیام و ناپدیدشدن آن از Inbox برنامه
- [ ] رفع مسدودی فقط از فهرست تنظیمات
- [ ] خروجی JSON و بازیابی تنظیمات با تأیید کاربر
- [ ] ترکیب search و category filter
- [ ] Category visibility
- [ ] Category activation
- [ ] Block Filter قبل از classification
- [ ] عدم حذف SMS اصلی هنگام block
- [ ] Rule-based classification
- [ ] OTP extraction اولیه
- [ ] manual category override
- [ ] بازگشت از override به auto classification
- [ ] soft delete تکی و دسته‌جمعی بدون حذف SMS سیستم
- [ ] نمایش و بازیابی پیام‌ها از سطل زباله
- [ ] Reply از کارت پیام با باز شدن SMS app گوشی
- [ ] light/dark theme
- [ ] local persistence در SharedPreferences
- [ ] Auto Copy پیش‌فرض روشن برای OTP

## Deferred / disabled features

- [ ] استخراج و نمایش مبلغ تراکنش فعلاً غیرفعال است؛ در نسخه‌های پیشرفته‌تر قابل بازگشت است.

## Live SMS / notification

- [ ] دریافت `SMS_RECEIVED`
- [ ] پشتیبانی از SMS چندقسمتی
- [ ] جلوگیری از duplicate notification
- [ ] classification پیام تازه با Ruleهای فعلی
- [ ] Notification مستقل برای هر category
- [ ] فعال/غیرفعال بودن Notification هر category
- [ ] صدای قابل انتخاب برای هر category
- [ ] پیش‌فرض بدون صدا
- [ ] بدون vibration
- [ ] رعایت Notification settings و volume خود Android
- [ ] باز شدن پیام مربوطه با لمس Notification
- [ ] نمایش کد OTP در Notification برای پیامک دسته کد تأیید
- [ ] کپی OTP در Clipboard هنگام دریافت SMS در پس‌زمینه، در صورت فعال بودن Auto Copy
- [ ] battery restriction guidance در اولین اجرای مناسب

## کیفیت

- [ ] Unit tests سبز
- [ ] Debug build سبز
- [ ] هیچ regression شناخته‌شده بدون ثبت در PRODUCT_FEEDBACK باقی نماند
- [ ] تست دستگاه واقعی برای تغییرات مرتبط با SMS/Notification انجام شود

## وضعیت Notification Sound

مشکل صدای Notification که در تست عملی مشاهده شده بود، طبق تست کاربر فعلاً حل‌شده است. در نسخه‌های بعد فقط regression test روی sound/Heads-up/channel انجام شود و از تغییر حدسی معماری خودداری شود.

## قانون

این فایل checklist مرجع QA است، نه feature list. اضافه شدن قابلیت جدید نباید باعث حذف یا تضعیف موارد موجود شود.
