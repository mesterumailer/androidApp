# Release Process

این فایل قرارداد انتشار پروژه است. هدف آن جلوگیری از انتشار APKی است که با commit آزمایش‌شده یا تغییرات تأییدشده یکسان نیست.

## اصل اصلی

هیچ APK مشتری از branchهای feature منتشر نمی‌شود.

مسیر رسمی انتشار:

```
feature/<work>
    |
    v
PR / CI
    |
    v
release/vX.Y.Z
    |
    +--> Release Candidate (signed)
    |
    +--> Device Test on the exact candidate commit
    |
    v
tag vX.Y.Z on the exact release-branch HEAD
    |
    v
Official GitHub Release (signed APK + SHA-256 + release manifest)
```

## 1. Development

هر قابلیت یا bug fix روی branch جداگانه توسعه داده می‌شود.

برای هر تغییر مرتبط با رفتار موجود:

- Root cause قبل از تغییر مشخص شود.
- تغییر حداقلی انجام شود.
- Unit/regression test اضافه یا اصلاح شود.
- CI باید سبز باشد.
- تغییرات مهم در README / PROJECT_HANDOFF / REGRESSION_BASELINE در صورت نیاز ثبت شوند.

## 2. Release Branch

وقتی نسخه آماده تست نهایی شد:

```
release/vX.Y.Z
```

باید دقیقاً با `versionName = "X.Y.Z"` در `app/build.gradle.kts` مطابقت داشته باشد.

با push روی release branch، workflow عمومی `.github/workflows/android.yml` به‌صورت خودکار CI را اجرا می‌کند.

برای ساخت **Release Candidate امضاشده**، workflow رسمی Release را به‌صورت دستی و با انتخاب همان `release/vX.Y.Z` branch از GitHub Actions اجرا می‌کنیم:

`.github/workflows/android-release.yml`

این workflow:

- versionName و versionCode را بررسی می‌کند.
- نام branch را با version تطبیق می‌دهد.
- تست‌های واحد را اجرا می‌کند.
- release APK واقعی را با release keystore می‌سازد.
- امضای APK را با `apksigner` بررسی می‌کند.
- APK، SHA-256 و فایل `RELEASE_CANDIDATE.txt` را به‌عنوان Artifact نگه می‌دارد.

در حالت `workflow_dispatch` این workflow فقط Candidate می‌سازد و **هیچ GitHub Release رسمی ایجاد نمی‌کند**.

دستی بودن این مرحله عمدی است: Release Candidate با release keystore ساخته می‌شود و باید یک گیت انسانی برای Device Test قبل از انتشار رسمی وجود داشته باشد.

## 3. Device Test Gate

فایل `RELEASE_CANDIDATE.txt` commit دقیق candidate را ثبت می‌کند.

APK تست‌شده باید از همان candidate استفاده شود.

تا زمانی که Device Test برای همان commit تأیید نشده است:

- tag انتشار ساخته نشود.
- نسخه به مشتری تحویل نشود.
- commit جدیدی روی release branch که candidate را عوض کند بدون تکرار تست وارد نشود.

برای تغییرات SMS/Notification، تست دستگاه واقعی الزامی است.

## 4. Official Release

پس از تأیید Device Test، tag دقیقاً روی HEAD همان release branch ساخته می‌شود:

```
vX.Y.Z
```

پس از تأیید Device Test، tag دقیقاً روی HEAD همان release branch ساخته می‌شود و همان workflow:

`.github/workflows/android-release.yml`

فقط با push روی tag اجرا می‌شود و قبل از انتشار چند gate اجباری دارد:

1. tag با `versionName` مطابقت داشته باشد.
2. branch `release/vX.Y.Z` وجود داشته باشد.
3. tag دقیقاً روی HEAD همان branch باشد.
4. GitHub Release قبلی با همان tag وجود نداشته باشد.
5. `versionCode` از آخرین release بیشتر باشد.
6. تست‌های واحد سبز باشند.
7. release APK با release keystore ساخته شود.
8. `apksigner verify --verbose` موفق باشد.
9. package name برابر `com.mesterumailer.smsmanager` باشد.
10. versionName داخل APK با release metadata مطابقت داشته باشد.

فقط پس از عبور همه این مراحل، GitHub Release رسمی ایجاد می‌شود.

## 5. Release Assets

هر Release رسمی باید حداقل این موارد را داشته باشد:

- `SmsManager-X.Y.Z.apk`
- `SmsManager-X.Y.Z.apk.sha256`
- `RELEASE_MANIFEST.txt`

فایل manifest شامل این موارد است:

- version
- versionCode
- tag
- commit
- APK SHA-256
- وضعیت signature
- source release branch

## 6. Rules that prevent recurrence

- workflow قدیمی که از feature branch مستقیماً Release تولید کند ممنوع است.
- release branch نباید APK مشتری را مستقیماً publish کند.
- build تستی و release نهایی دو مرحله جدا هستند.
- commit دقیق candidate باید قبل از tag مشخص و قابل ردیابی باشد.
- tag انتشار فقط پس از Device Test مجاز است.
- تغییر بعد از Device Test یعنی candidate جدید و تست مجدد.
- ادعای «این fix داخل release است» باید با compare commitها یا source همان tag قابل اثبات باشد.

## 7. QA Checklist

### Automated

- [ ] Unit tests PASS
- [ ] Debug build PASS
- [ ] Release Candidate build PASS
- [ ] Release signature PASS
- [ ] APK identity PASS
- [ ] versionName/tag PASS
- [ ] versionCode monotonic PASS
- [ ] tag == release branch HEAD PASS

### Manual

- [ ] Device Test روی همان Release Candidate commit انجام شده
- [ ] SMS-related behavior بررسی شده
- [ ] Notification-related behavior بررسی شده
- [ ] هیچ regression شناخته‌شده باقی نمانده

## 8. Customer Handoff

هنگام تحویل مشتری فقط لینک GitHub Release رسمی ارائه شود، نه debug APK و نه Release Candidate Artifact.

برای هر تحویل، commit و SHA-256 موجود در Release Manifest باید با Release page یکسان باشد.
