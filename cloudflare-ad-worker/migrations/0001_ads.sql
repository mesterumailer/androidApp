-- Cloudflare D1 schema for the SMS Manager bottom-banner ads.
CREATE TABLE IF NOT EXISTS ads (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  slug TEXT NOT NULL UNIQUE,
  placement TEXT NOT NULL DEFAULT 'home_bottom',
  title TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  cta_text TEXT NOT NULL DEFAULT 'مشاهده',
  image_url TEXT,
  target_url TEXT NOT NULL CHECK (substr(target_url, 1, 8) = 'https://'),
  is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
  priority INTEGER NOT NULL DEFAULT 100,
  starts_at TEXT,
  ends_at TEXT,
  click_count INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ads_active_placement
ON ads (placement, is_active, priority DESC);

INSERT INTO ads (
  slug, placement, title, description, cta_text,
  image_url, target_url, is_active, priority
)
VALUES (
  'yajing-chinese',
  'home_bottom',
  'آموزش زبان چینی',
  'برای آشنایی با آموزش زبان چینی، از وب‌سایت یاجینگ دیدن کنید.',
  'مشاهده سایت',
  NULL,
  'https://yajingchinese.ir',
  1,
  100
)
ON CONFLICT(slug) DO UPDATE SET
  placement = excluded.placement,
  title = excluded.title,
  description = excluded.description,
  cta_text = excluded.cta_text,
  target_url = excluded.target_url,
  is_active = excluded.is_active,
  priority = excluded.priority,
  updated_at = CURRENT_TIMESTAMP;
