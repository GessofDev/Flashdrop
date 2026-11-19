ALTER TABLE internal.delivery_routes
    ADD COLUMN IF NOT EXISTS pickup_latitude numeric(9,6),
    ADD COLUMN IF NOT EXISTS pickup_longitude numeric(9,6),
    ADD COLUMN IF NOT EXISTS client_user_id varchar(64);

ALTER TABLE internal.delivery_routes
    DROP CONSTRAINT IF EXISTS chk_delivery_routes_pickup_lat_range,
    DROP CONSTRAINT IF EXISTS chk_delivery_routes_pickup_lng_range,
    DROP CONSTRAINT IF EXISTS chk_delivery_routes_pickup_coords_pair;

ALTER TABLE internal.delivery_routes
    ADD CONSTRAINT chk_delivery_routes_pickup_lat_range
        CHECK (pickup_latitude IS NULL OR (pickup_latitude >= -90 AND pickup_latitude <= 90)),
    ADD CONSTRAINT chk_delivery_routes_pickup_lng_range
        CHECK (pickup_longitude IS NULL OR (pickup_longitude >= -180 AND pickup_longitude <= 180)),
    ADD CONSTRAINT chk_delivery_routes_pickup_coords_pair
        CHECK ((pickup_latitude IS NULL) = (pickup_longitude IS NULL));
