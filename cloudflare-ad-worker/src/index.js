const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, OPTIONS",
  "access-control-allow-headers": "Content-Type",
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
}

function activeAdsSql() {
  return `
    SELECT id, slug, placement, title, description, cta_text, image_url,
           target_url, priority, weight, updated_at
    FROM ads
    WHERE is_active = 1
      AND placement = ?
      AND weight > 0
      AND (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP)
      AND (ends_at IS NULL OR ends_at >= CURRENT_TIMESTAMP)
    ORDER BY priority DESC, id ASC
  `;
}

async function selectWeightedAd(db, placement) {
  const result = await db.prepare(activeAdsSql()).bind(placement).all();
  const ads = result?.results || [];
  if (!ads.length) return null;

  const totalWeight = ads.reduce((sum, ad) => sum + Math.max(0, Number(ad.weight) || 0), 0);
  if (totalWeight <= 0) return null;

  await db.prepare(`
    INSERT OR IGNORE INTO ads_rotation_state (placement, counter)
    VALUES (?, 0)
  `).bind(placement).run();

  // Atomic increment: concurrent requests receive unique rotation slots.
  const rotation = await db.prepare(`
    UPDATE ads_rotation_state
    SET counter = counter + 1, updated_at = CURRENT_TIMESTAMP
    WHERE placement = ?
    RETURNING counter
  `).bind(placement).first();

  const sequence = Number(rotation?.counter);
  if (!Number.isFinite(sequence) || sequence < 1) {
    throw new Error("Ad rotation counter is unavailable");
  }

  let slot = (sequence - 1) % totalWeight;
  for (const ad of ads) {
    const weight = Math.max(0, Number(ad.weight) || 0);
    if (slot < weight) {
      // This is a selection/impression opportunity, not proof of visible pixels.
      try {
        await db.prepare(`
          UPDATE ads SET impressions = impressions + 1, updated_at = CURRENT_TIMESTAMP
          WHERE id = ?
        `).bind(ad.id).run();
      } catch {
        // Do not hide a valid ad if aggregate analytics are temporarily unavailable.
      }
      return ad;
    }
    slot -= weight;
  }
  return null;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: JSON_HEADERS });
    }

    if (request.method !== "GET") {
      return json({ ok: false, error: "method_not_allowed" }, 405);
    }

    if (url.pathname === "/health") {
      return json({ ok: true, service: "sms-manager-ads", database: Boolean(env.DB) });
    }

    if (!env.DB) {
      return json({ ok: false, error: "database_binding_missing" }, 503);
    }

    if (url.pathname === "/api/v1/ads/active") {
      const placement = (url.searchParams.get("placement") || "home_bottom").trim();
      if (!/^[a-z0-9_-]{1,40}$/.test(placement)) {
        return json({ ok: false, error: "invalid_placement" }, 400);
      }

      try {
        const ad = await selectWeightedAd(env.DB, placement);
        if (!ad) return json({ ad: null });

        const origin = url.origin;
        return json({
          ad: {
            id: ad.id,
            slug: ad.slug,
            title: ad.title,
            description: ad.description || "",
            cta_text: ad.cta_text || "مشاهده",
            image_url: ad.image_url && ad.image_url.startsWith("https://")
              ? ad.image_url
              : `${origin}/api/v1/ads/${encodeURIComponent(ad.slug)}/banner`,
            click_url: `${origin}/r/${encodeURIComponent(ad.slug)}`,
          },
        });
      } catch {
        return json({ ok: false, error: "database_read_failed" }, 500);
      }
    }

    const bannerMatch = url.pathname.match(/^\/api\/v1\/ads\/([a-z0-9-]{1,80})\/banner$/);
    if (bannerMatch) {
      const slug = bannerMatch[1];
      try {
        const asset = await env.DB.prepare(`
          SELECT image_mime, image_base64
          FROM ads
          WHERE slug = ?
            AND is_active = 1
            AND (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP)
            AND (ends_at IS NULL OR ends_at >= CURRENT_TIMESTAMP)
          LIMIT 1
        `).bind(slug).first();

        if (!asset || !asset.image_base64) {
          return new Response("Banner image not found", { status: 404 });
        }

        const binary = atob(asset.image_base64);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
          bytes[i] = binary.charCodeAt(i);
        }
        return new Response(bytes, {
          headers: {
            "content-type": asset.image_mime || "image/webp",
            "cache-control": "public, max-age=3600",
            "x-content-type-options": "nosniff",
          },
        });
      } catch {
        return new Response("Banner image unavailable", { status: 500 });
      }
    }

    const redirectMatch = url.pathname.match(/^\/r\/([a-z0-9-]{1,80})$/);
    if (redirectMatch) {
      const slug = redirectMatch[1];
      try {
        const ad = await env.DB.prepare(`
          SELECT target_url
          FROM ads
          WHERE slug = ?
            AND is_active = 1
            AND (starts_at IS NULL OR starts_at <= CURRENT_TIMESTAMP)
            AND (ends_at IS NULL OR ends_at >= CURRENT_TIMESTAMP)
          LIMIT 1
        `).bind(slug).first();

        if (!ad || typeof ad.target_url !== "string") {
          return new Response("Ad not found", { status: 404 });
        }

        const target = new URL(ad.target_url);
        if (target.protocol !== "https:") {
          return new Response("Invalid ad destination", { status: 400 });
        }

        // Click analytics must never prevent the visitor from reaching the advertiser.
        // Older/manual D1 schemas may not have click_count yet, so treat this as best-effort.
        try {
          await env.DB.prepare(
            "UPDATE ads SET click_count = click_count + 1, updated_at = CURRENT_TIMESTAMP WHERE slug = ?"
          ).bind(slug).run();
        } catch {
          // Intentionally ignored: redirect availability is more important than analytics.
        }

        return Response.redirect(target.toString(), 302);
      } catch {
        return new Response("Ad lookup unavailable. Check the ads table and DB binding.", { status: 503 });
      }
    }

    return json({ ok: false, error: "not_found" }, 404);
  },
};
