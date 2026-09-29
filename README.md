ALTER TABLE productos
    ADD COLUMN IF NOT EXISTS firebase_uid VARCHAR(128) NOT NULL DEFAULT '';
 
CREATE INDEX IF NOT EXISTS idx_productos_firebase_uid
    ON productos (firebase_uid);
