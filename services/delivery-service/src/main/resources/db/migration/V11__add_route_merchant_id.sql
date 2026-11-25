ALTER TABLE internal.delivery_routes
    ADD COLUMN IF NOT EXISTS merchant_id bigint;

CREATE INDEX IF NOT EXISTS idx_delivery_routes_merchant_id
    ON internal.delivery_routes (merchant_id);
