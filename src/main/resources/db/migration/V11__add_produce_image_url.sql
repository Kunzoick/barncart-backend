-- Nullable — existing produce has no image, admin adds URLs manually
-- VARCHAR(500) — standard URL length, covers CDN and storage service URLs
ALTER TABLE produce
    ADD COLUMN image_url VARCHAR(500) NULL;