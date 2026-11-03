import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

function makeEnv(ad) {
  let clickCount = 0;
  const DB = {
    prepare(sql) {
      return {
        bind(...args) {
          return {
            async first() {
              if (sql.includes("SELECT id, slug")) return ad;
              if (sql.includes("SELECT image_mime")) return ad;
              if (sql.includes("SELECT target_url")) return ad;
              return null;
            },
            async run() {
              if (sql.includes("UPDATE ads SET click_count")) clickCount += 1;
              return { success: true };
            },
          };
        },
      };
    },
  };
  return { env: { DB }, getClickCount: () => clickCount };
}

const sampleAd = {
  id: 1,
  slug: "yajing-chinese",
  placement: "home_bottom",
  title: "آموزش زبان چینی",
  description: "یادگیری زبان چینی",
  cta_text: "مشاهده سایت",
  image_url: null,
  image_mime: "image/webp",
  image_base64: Buffer.from("fake-webp-image").toString("base64"),
  target_url: "https://yajingchinese.ir",
  priority: 100,
  updated_at: "2026-11-03 00:00:00",
};

test("health endpoint reports service state", async () => {
  const { env } = makeEnv(sampleAd);
  const response = await worker.fetch(new Request("https://unit.test/health"), env);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    ok: true,
    service: "sms-manager-ads",
    database: true,
  });
});

test("active ad includes D1 text, image endpoint and click redirect", async () => {
  const { env } = makeEnv(sampleAd);
  const response = await worker.fetch(
    new Request("https://unit.test/api/v1/ads/active?placement=home_bottom"),
    env
  );
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.ad.title, "آموزش زبان چینی");
  assert.equal(body.ad.image_url, "https://unit.test/api/v1/ads/yajing-chinese/banner");
  assert.equal(body.ad.click_url, "https://unit.test/r/yajing-chinese");
});

test("banner endpoint returns the image bytes stored in D1", async () => {
  const { env } = makeEnv(sampleAd);
  const response = await worker.fetch(
    new Request("https://unit.test/api/v1/ads/yajing-chinese/banner"),
    env
  );
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("content-type"), "image/webp");
  assert.equal(await response.text(), "fake-webp-image");
});

test("click route redirects only to the HTTPS target and counts the click", async () => {
  const { env, getClickCount } = makeEnv(sampleAd);
  const response = await worker.fetch(new Request("https://unit.test/r/yajing-chinese"), env);
  assert.equal(response.status, 302);
  assert.equal(response.headers.get("location"), "https://yajingchinese.ir/");
  assert.equal(getClickCount(), 1);
});

test("non-HTTPS destinations are rejected", async () => {
  const { env } = makeEnv({ ...sampleAd, target_url: "http://example.com" });
  const response = await worker.fetch(new Request("https://unit.test/r/yajing-chinese"), env);
  assert.equal(response.status, 400);
});
