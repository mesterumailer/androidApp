# Regression Baseline

این فایل رفتارهایی را مشخص می‌کند که در مرحله بهبود محصول باید حفظ شوند. هر تغییر جدید باید قبل و بعد از خود، این baseline را در نظر بگیرد.

## Core behavior

- [ ] خواندن SMSهای موجود از `Telephony.Sms.Inbox`
- [ ] پیش‌فرض Inbox برابر 1000 پیام
- [ ] حداقل limit برابر 200 و حداکثر 5000
- [ ] انتخاب محدوده «آخرین پیام‌ها» یا «تا تاریخ مشخص»
- [ ] ترتیب newest/oldest
- [ ] جست‌وجو در body
- [ ] جست‌وجو در sender/address
- [ ] ترکیب search و category filter
- [ ] Category visibility
- [ ] Category activation
- [ ] Block Filter قبل از classification
- [ ] عدم حذف SMS اصلی هنگام block
- [ ] Rule-based classification
- [ ] OTP extraction اولیه
- [ ] transaction amount extraction اولیه
- [ ] manual category override
- [ ] بازگشت از override به auto classification
- [ ] light/dark theme
- [ ] local persistence در SharedPreferences

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

## کیفیت

- [ ] Unit tests سبز
- [ ] Debug build سبز
- [ ] هیچ regression شناخته‌شده بدون ثبت در PRODUCT_FEEDBACK باقی نماند
- [ ] تست دستگاه واقعی برای تغییرات مرتبط با SMS/Notification انجام شود

## Known issue در شروع Improvement Phase

Notification sound در تست واقعی کاربر انتخاب شده ولی هنگام دریافت SMS صدایی پخش نشده است.

این مورد هنوز root cause قطعی ندارد و نباید بدون تست واقعی و بررسی Notification Channel به‌صورت حدسی در منطق اصلی اصلاح شود.

## قانون

این فایل checklist مرجع QA است، نه feature list. اضافه شدن قابلیت جدید نباید باعث حذف یا تضعیف موارد موجود شود.
