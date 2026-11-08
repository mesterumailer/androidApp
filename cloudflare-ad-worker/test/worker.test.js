import test from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

function makeEnv(ads, { failClickUpdate = false } = {}) {
  let clickCount = 0;
  let rotationCounter = 0;
  const impressions = new Map();
  const rows = Array.isArray(ads) ? ads : [ads];
  const DB = {
    prepare(sql) {
      return {
        bind(...args) {
          return {
            async first() {
              if (sql.includes("RETURNING counter")) return { counter: ++rotationCounter };
              if (sql.includes("SELECT target_url")) return rows.find((item) => item.slug === args[0]) || null;
              if (sql.includes("SELECT image_mime")) return rows.find((item) => item.slug === args[0]) || null;
              return null;
            },
            async all() {
              if (sql.includes("FROM ads") && sql.includes("weight")) {
                return { results: rows.filter((item) => item.is_active !== 0 && item.placement === args[0] && item.weight > 0) };
              }
              return { results: [] };
            },
            async run() {
              if (sql.includes("UPDATE ads SET click_count")) {
                if (failClickUpdate) throw new Error("click_count column missing");
                clickCount += 1;
              }
              if (sql.includes("UPDATE ads SET impressions")) {
                const id = args[0];
                impressions.set(id, (impressions.get(id) || 0) + 1);
              }
              return { success: true, meta: { changes: 1 } };
            },
          };
        },
      };
    },
  };
  return {
    env: { DB },
    getClickCount: () => clickCount,
    getImpressions: (id) => impressions.get(id) || 0,
  };
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
  weight: 3,
  is_active: 1,
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

test("redirect still succeeds when aggregate click tracking fails", async () => {
  const { env, getClickCount } = makeEnv(sampleAd, { failClickUpdate: true });
  const response = await worker.fetch(new Request("https://unit.test/r/yajing-chinese"), env);
  assert.equal(response.status, 302);
  assert.equal(response.headers.get("location"), "https://yajingchinese.ir/");
  assert.equal(getClickCount(), 0);
});

test("non-HTTPS destinations are rejected", async () => {
  const { env } = makeEnv({ ...sampleAd, target_url: "http://example.com" });
  const response = await worker.fetch(new Request("https://unit.test/r/yajing-chinese"), env);
  assert.equal(response.status, 400);
});

test("active ad endpoint follows a 3-to-1 weighted rotation and counts selections", async () => {
  const secondAd = {
    ...sampleAd,
    id: 2,
    slug: "campaign-two",
    title: "تبلیغ دوم",
    weight: 1,
    image_base64: null,
  };
  const { env, getImpressions } = makeEnv([sampleAd, secondAd]);
  const selected = [];
  for (let index = 0; index < 4; index += 1) {
    const response = await worker.fetch(
      new Request("https://unit.test/api/v1/ads/active?placement=home_bottom"),
      env
    );
    assert.equal(response.status, 200);
    selected.push((await response.json()).ad.slug);
  }
  assert.deepEqual(selected, ["yajing-chinese", "yajing-chinese", "yajing-chinese", "campaign-two"]);
  assert.equal(getImpressions(1), 3);
  assert.equal(getImpressions(2), 1);
});

test("disabled ads are excluded from weighted rotation", async () => {
  const disabledAd = { ...sampleAd, id: 2, slug: "disabled", is_active: 0, weight: 100 };
  const { env } = makeEnv([sampleAd, disabledAd]);
  const response = await worker.fetch(
    new Request("https://unit.test/api/v1/ads/active?placement=home_bottom"),
    env
  );
  assert.equal((await response.json()).ad.slug, "yajing-chinese");
});
