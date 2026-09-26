# Product Feedback

این فایل دفتر ثبت بازخورد واقعی استفاده از محصول است.

## وضعیت‌ها

- **Observed:** مشکل یا رفتار واقعاً مشاهده شده است.
- **Investigating:** در حال بررسی root cause.
- **Fixed:** اصلاح کد انجام شده ولی هنوز باید regression/device test تکمیل شود.
- **Verified:** اصلاح در تست مناسب تأیید شده است.
- **Rejected/Deferred:** فعلاً اجرا نمی‌شود؛ دلیل باید ثبت شود.

## 2026-09-25 — Notification sound

**Type:** Bug  
**Status:** Investigating

**GitHub Issue:** [#4](https://github.com/mesterumailer/androidApp/issues/4)

### مشاهده

در تست عملی، برای یک category صدای اعلان انتخاب شده بود، اما هنگام دریافت SMS جدید و نمایش Notification هیچ صدایی پخش نشد.

### رفتار مورد انتظار

وقتی Notification آن category فعال است و یک sound معتبر برای آن انتخاب شده، Notification باید با رفتار صوتی تعیین‌شده توسط Android برای channel مربوطه ارائه شود؛ مگر اینکه کاربر یا سیستم Android آن channel/notification را بی‌صدا کرده باشد.

### وضعیت فنی فعلی

- Notificationها روی Android O+ از Notification Channel استفاده می‌کنند.
- channel بر اساس category ساخته می‌شود.
- sound و importance هنگام ساخت channel تعیین می‌شوند.
- تغییر sound از UI فعلاً باعث delete/recreate شدن channel می‌شود.
- Android اجازه نمی‌دهد رفتار صوتی channel موجود به‌صورت عادی بعد از ایجاد تغییر کند و تنظیمات نهایی channel در اختیار کاربر/سیستم است.

### شواهد لازم برای root cause

در تست بعدی باید حداقل این موارد بررسی شوند:

1. Android version و ROM/device
2. category و channel ID مورد استفاده
3. `NotificationChannel.getImportance()`
4. `NotificationChannel.getSound()`
5. وضعیت notifications برای app و channel
6. Notification volume دستگاه
7. نتیجه Test Notification مستقل از SMS
8. نتیجه بعد از تغییر sound و ساخت channel جدید
9. نتیجه بعد از reboot

### محدودیت

تا وقتی شواهد بالا بررسی نشده، تغییر speculative در Notification architecture پذیرفته نیست.

---

## قواعد ثبت بازخورد

هر بازخورد جدید باید تا حد امکان شامل این اطلاعات باشد:

- چه چیزی دیده شد؟
- چه زمانی/در چه شرایطی؟
- رفتار مورد انتظار چه بود؟
- device/Android version در موارد device-specific
- آیا همیشه reproducible است؟
- priority
- وضعیت بررسی
- تست لازم برای بستن مورد

بازخوردهای «ایده» و «مشکل واقعی» باید از هم جدا بمانند.
