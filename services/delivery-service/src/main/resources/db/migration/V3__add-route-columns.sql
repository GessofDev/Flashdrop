-- V3__add-route-columns.sql
-- Aligns delivery_routes schema with DeliveryRouteJpaEntity.
-- V1__create-tables.sql omitted pickup_address, delivery_address, distance_km, estimated_minutes
-- (these are part of the domain model; addressed here as an additive migration so existing
-- environments do not need a destructive V1 rewrite).

ALTER TABLE internal.delivery_routes
    ADD COLUMN IF NOT EXISTS pickup_address VARCHAR(512),
    ADD COLUMN IF NOT EXISTS delivery_address VARCHAR(512),
    ADD COLUMN IF NOT EXISTS distance_km DECIMAL(8,2),
    ADD COLUMN IF NOT EXISTS estimated_minutes INTEGER;
