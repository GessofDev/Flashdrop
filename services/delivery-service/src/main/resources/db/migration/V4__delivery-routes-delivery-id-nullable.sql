-- V4__delivery-routes-delivery-id-nullable.sql
-- The delivery_routes.delivery_id FK references internal.delivery(id) but the JPA entity
-- does not populate it (delivery-service does not own the delivery lifecycle — orders-service does).
-- DROP NOT NULL so routes can be created referencing only an orderId, then resolved later.

ALTER TABLE internal.delivery_routes ALTER COLUMN delivery_id DROP NOT NULL;
