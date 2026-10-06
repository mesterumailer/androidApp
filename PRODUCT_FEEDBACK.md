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
**Status:** Fixed — pending device verification

**GitHub Issue:** [#4](https://github.com/mesterumailer/androidApp/issues/4)

### مشاهده

در تست عملی، برای یک category صدای اعلان انتخاب شده بود، اما هنگام دریافت SMS جدید و نمایش Notification هیچ صدایی پخش نشد.

### رفتار مورد انتظار

وقتی Notification آن category فعال است و یک sound معتبر برای آن انتخاب شده، Notification باید با رفتار صوتی تعیین‌شده توسط Android برای channel مربوطه ارائه شود؛ مگر اینکه کاربر یا سیستم Android آن channel/notification را بی‌صدا کرده باشد.

### اصلاح اول و دوم اعمال‌شده

- Channel ID به نسخه جدید `sms_category_v2_<categoryId>` منتقل شد تا Channel قدیمی با رفتار silent/low به نسخه جدید منتقل نشود.
- Importance همه Channelهای Notification روی `IMPORTANCE_HIGH` باقی ماند تا Heads-up/Pop-up امکان‌پذیر باشد.
- Channel قدیمی با prefix `sms_category_` هنگام مهاجرت حذف می‌شود.
- دریافت URI انتخاب‌شده از Ringtone Picker برای Android 13+ به overload جدید API منتقل شد.

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

## 2026-09-26 — Notification presentation

**Type:** Product requirement / UX feedback  
**Status:** Implemented — pending device verification

وقتی اعلان یک category فعال است، باید از نظر اهمیت در سطحی باشد که Android بتواند Notification را به شکل Heads-up / Pop-up / Floating نمایش دهد. برای Android O+ این رفتار از Channel importance می‌آید، بنابراین Channelهای این محصول برای اعلان‌های SMS با `IMPORTANCE_HIGH` ساخته می‌شوند.

توجه: Android و تنظیمات خود کاربر می‌توانند نمایش Pop-up را محدود کنند؛ بنابراین «امکان نمایش Pop-up» با «اجبار Pop-up در همه دستگاه‌ها» یکسان نیست.

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

## 2026-09-26 — Notification sound issue after first fix

**Type:** Bug / Regression during verification  
**Status:** Fixed — pending device verification

### مشاهده

بعد از اصلاح اول، در تست جدید مشخص شد که برخی categoryها، از جمله «خدمات» و «سایر»، هنوز sound را به‌درستی دریافت/اعمال نمی‌کنند.

### اصلاح دوم

Channel ID اکنون بر اساس category و sound انتخاب‌شده ساخته می‌شود. Channelهای قبلی همان category قبل از ایجاد Channel هدف پاک می‌شوند و Notification دقیقاً با همان Channel هدف ارسال می‌شود.

### Verification

- [ ] دریافت SMS از category «خدمات» با sound انتخابی
- [ ] دریافت SMS از category «سایر» با sound انتخابی
- [ ] بررسی Android Settings → Notification Category → Sound
- [ ] بررسی Heads-up/Pop-up
- [ ] تغییر sound و تکرار تست
- [ ] انتخاب «بدون صدا» و بررسی silent بودن
