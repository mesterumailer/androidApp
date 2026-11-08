-- Weighted banner rotation and aggregate impression analytics.
-- Existing campaigns remain active. Yajing starts with weight 3; new campaigns default to 1.
ALTER TABLE ads ADD COLUMN weight INTEGER NOT NULL DEFAULT 1 CHECK (weight BETWEEN 1 AND 1000);
ALTER TABLE ads ADD COLUMN impressions INTEGER NOT NULL DEFAULT 0 CHECK (impressions >= 0);

UPDATE ads
SET weight = 3, updated_at = CURRENT_TIMESTAMP
WHERE slug = 'yajing-chinese';

CREATE TABLE IF NOT EXISTS ads_rotation_state (
  placement TEXT PRIMARY KEY,
  counter INTEGER NOT NULL DEFAULT 0 CHECK (counter >= 0),
  updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO ads_rotation_state (placement, counter)
VALUES ('home_bottom', 0);
