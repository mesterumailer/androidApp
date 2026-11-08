# SMS Manager — Ads Admin Worker

This is a separate management application. Keep it separate from `sms-manager-ads`, because the public Android app must continue to fetch ads without an Access login.

## Cloudflare Access cost

Cloudflare Zero Trust Free supports up to 50 users at $0/month. The account setup flow may still ask for billing details when activating the Free plan; the Free plan itself has no monthly subscription charge. Check current official pricing: https://www.cloudflare.com/plans/

## Deploy with the Cloudflare Dashboard

1. Run `cloudflare-ad-worker/migrations/0002_weighted_rotation.sql` in the existing D1 database named `sms-manager-ads`. Run it after `0001_ads.sql`; do not re-run migration 0001 if the database already contains the ads table.
2. Create a **new** Worker named `sms-manager-ads-admin`.
3. In its editor, paste `cloudflare-ad-admin-worker/src/index.js` and deploy.
4. In Worker Settings → Bindings, add the existing D1 database:
   - Variable name: `DB`
   - Database: `sms-manager-ads`
5. Under Worker Settings → Domains & Routes, enable **Cloudflare Access** for this Worker. Protect the production `workers.dev` hostname. Do not enable Access on the public Worker `sms-manager-ads`.
6. In Zero Trust → Access → Applications, edit the app created for `sms-manager-ads-admin`. Add an Allow policy for only your administrator email address. Keep the default deny behavior for everyone else.
7. Open the admin Worker's `workers.dev` URL. Cloudflare Access should show the login challenge, then the dashboard.

The admin Worker checks `ctx.access.getIdentity()` on every request as defense in depth. If Access is not enabled, the dashboard and API return 403. The app is same-origin and does not enable permissive CORS.

## Dashboard functions

- Create ads with a unique English slug and WebP, PNG or JPEG banner (maximum 500 KB).
- Edit title, description, CTA text, destination URL, weight and priority.
- Enable or disable each ad without deleting it.
- Permanently delete a campaign after a confirmation prompt.
- Review aggregate impression-opportunity and click counters.
- Edit display weight (1–1000). Example: Yajing = 3 and Campaign B = 1 gives a 3:1 weighted cycle across public ad requests.

The default Yajing campaign is preserved and assigned weight 3 by migration 0002. New campaigns default to weight 1.

## Rotation behavior

The public Worker increments a D1 counter atomically with `UPDATE ... RETURNING`, then selects a slot from the sum of active campaign weights. This creates a deterministic weighted cycle across ad requests. It is global across all application launches/users, not a separate per-device counter. Disabled ads, expired ads, future ads and ads with non-positive weight are not eligible.

The `impressions` value counts successful ad-selection responses from the public Worker (an impression opportunity), not a guaranteed on-screen pixel view. Clicks remain aggregate counts. No SMS contents, sender identifiers, contacts or device IDs are collected.

## Important separation

- Public Worker: `sms-manager-ads` — endpoints consumed by Android; keep public.
- Admin Worker: `sms-manager-ads-admin` — dashboard and write API; protect the entire Worker with Cloudflare Access.
- Both Workers bind to the same D1 database using the binding name `DB`.
