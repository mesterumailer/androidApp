# SMS Manager — Cloudflare Ads Worker

This Worker serves one active bottom-banner ad to the Android application and redirects banner clicks to the destination stored in D1.

## Components

- Worker name: `sms-manager-ads`
- Suggested public API: `https://sms-manager-ads.mesterumailer.workers.dev`
- D1 database name: `sms-manager-ads`
- Required D1 binding name: `DB`
- No admin API is exposed. Edit ad records through the Cloudflare D1 console. The initial WebP banner bytes are stored in the D1 row itself; R2 is not required.

## Deploy with Cloudflare Dashboard

1. In Cloudflare Dashboard, create a D1 database named `sms-manager-ads`.
2. Open the database SQL Console and run the complete contents of `migrations/0001_ads.sql`. This creates the table and inserts the initial Yajing Chinese ad.
3. Create a Worker named `sms-manager-ads`.
4. Paste the contents of `src/index.js` into the Worker's editor and deploy.
5. Open Worker Settings → Bindings → Add → D1 Database. Set the variable name to exactly `DB`, select the `sms-manager-ads` database, save and deploy.
6. Verify:
   - `/health`
   - `/api/v1/ads/active?placement=home_bottom`
   - `/api/v1/ads/yajing-chinese/banner` (this should return the WebP image stored in D1).\n   - `/r/yajing-chinese` (this should redirect to `https://yajingchinese.ir`).

The app expects the account subdomain `mesterumailer.workers.dev`, based on the existing project Worker domain. If Cloudflare assigns a different subdomain, update `HomeAdService.API_URL` in the Android project before building the APK.

## Ad fields in D1

| Field | Purpose |
|---|---|
| `slug` | Stable ad identifier used by the redirect route |
| `placement` | Banner location; the app requests `home_bottom` |
| `title`, `description`, `cta_text` | Text shown in the Android banner |
| `image_base64` / `image_mime` | Banner image bytes and MIME type stored in D1 (the initial ad uses WebP) |\n| `image_url` | Optional external HTTPS image override; when empty, the Worker serves the image stored in D1 |
| `target_url` | HTTPS destination opened after a click |
| `is_active` | Set to 0 to hide the ad or 1 to show it |
| `priority` | Higher values are selected first |
| `starts_at`, `ends_at` | Optional UTC scheduling window |
| `click_count` | Aggregate count of ad clicks; no user/device identifier is stored |

To change the text or destination, update the row in D1. Example:

```sql
UPDATE ads
SET title = 'آموزش زبان چینی',
    description = 'یادگیری زبان چینی',
    cta_text = 'مشاهده سایت',
    target_url = 'https://yajingchinese.ir',
    is_active = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE slug = 'yajing-chinese';
```

To temporarily disable all display of this ad:

```sql
UPDATE ads SET is_active = 0, updated_at = CURRENT_TIMESTAMP
WHERE slug = 'yajing-chinese';
```

## Banner image update\n\nThe first banner image is stored in the `image_base64` column by the migration, so the first version does not need R2. To replace it, update `image_base64` (base64-encoded WebP or PNG) and `image_mime` using the D1 console. Alternatively, set `image_url` to a public HTTPS image URL to use an externally hosted image.\n\n## Privacy

The active-ad endpoint receives only a normal HTTPS GET for the `home_bottom` placement. The Android app does not send SMS contents, sender numbers, contacts, or account identifiers. Clicks increment an aggregate counter. A click is redirected to the HTTPS target URL stored in D1.
