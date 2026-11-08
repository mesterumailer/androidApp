import test from "node:test";
import assert from "node:assert/strict";
import worker from "../../cloudflare-ad-admin-worker/src/index.js";

function makeContext(authenticated = true) {
  return {
    access: {
      async getIdentity() {
        return authenticated ? { email: "admin@example.com" } : null;
      },
    },
  };
}

function makeEnv() {
  return {
    DB: {
      prepare(sql) {
        return {
          bind() {
            return {
              async all() {
                if (sql.includes("FROM ads")) {
                  return {
                    results: [{
                      id: 1, slug: "yajing-chinese", placement: "home_bottom",
                      title: "آموزش زبان چینی", description: "یادگیری زبان چینی",
                      cta_text: "مشاهده سایت", target_url: "https://yajingchinese.ir",
                      is_active: 1, priority: 100, weight: 3, impressions: 12,
                      click_count: 2, image_url: null, starts_at: null, ends_at: null,
                      updated_at: "2026-11-09 00:00:00",
                    }],
                  };
                }
                return { results: [] };
              },
              async first() {
                return {
                  image_mime: "image/webp",
                  image_base64: "UklGRg==",
                  image_url: null,
                };
              },
              async run() {
                return { success: true, meta: { changes: 1 } };
              },
            };
          },
        };
      },
    },
  };
}

test("admin dashboard is denied without a Cloudflare Access identity", async () => {
  const response = await worker.fetch(
    new Request("https://admin.test/"),
    makeEnv(),
    makeContext(false)
  );
  assert.equal(response.status, 403);
});

test("authenticated admin can load the dashboard", async () => {
  const response = await worker.fetch(
    new Request("https://admin.test/"),
    makeEnv(),
    makeContext(true)
  );
  assert.equal(response.status, 200);
  assert.match(await response.text(), /مدیریت تبلیغات پیامک‌یار/);
  assert.match(response.headers.get("content-security-policy"), /frame-ancestors 'none'/);
});

test("authenticated admin sees campaign weight and aggregate statistics", async () => {
  const response = await worker.fetch(
    new Request("https://admin.test/api/admin/ads"),
    makeEnv(),
    makeContext(true)
  );
  assert.equal(response.status, 200);
  const payload = await response.json();
  assert.equal(payload.ads[0].slug, "yajing-chinese");
  assert.equal(payload.ads[0].weight, 3);
  assert.equal(payload.ads[0].impressions, 12);
  assert.equal(payload.ads[0].click_count, 2);
});

test("campaign image previews remain available when an ad is disabled", async () => {
  const response = await worker.fetch(
    new Request("https://admin.test/api/admin/ads/1/image"),
    makeEnv(),
    makeContext(true)
  );
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-type"), "image/webp");
});
