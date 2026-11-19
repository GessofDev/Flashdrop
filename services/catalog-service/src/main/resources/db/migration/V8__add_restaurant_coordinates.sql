ALTER TABLE public.restaurant
    ADD COLUMN IF NOT EXISTS latitude numeric(9,6),
    ADD COLUMN IF NOT EXISTS longitude numeric(9,6);

ALTER TABLE public.restaurant
    DROP CONSTRAINT IF EXISTS chk_restaurant_latitude_range,
    DROP CONSTRAINT IF EXISTS chk_restaurant_longitude_range,
    DROP CONSTRAINT IF EXISTS chk_restaurant_coordinates_pair;

ALTER TABLE public.restaurant
    ADD CONSTRAINT chk_restaurant_latitude_range
        CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)),
    ADD CONSTRAINT chk_restaurant_longitude_range
        CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)),
    ADD CONSTRAINT chk_restaurant_coordinates_pair
        CHECK ((latitude IS NULL) = (longitude IS NULL));
