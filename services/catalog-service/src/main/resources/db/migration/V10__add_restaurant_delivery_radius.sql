ALTER TABLE restaurant ADD COLUMN delivery_radius_km NUMERIC(4, 2);
ALTER TABLE restaurant ADD CONSTRAINT restaurant_delivery_radius_range
    CHECK (delivery_radius_km IS NULL OR delivery_radius_km BETWEEN 1 AND 50);
