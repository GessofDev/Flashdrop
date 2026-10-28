-- V7__delivery-routes-reception-mode.sql
-- Entrega con codigo de verificacion: la app del repartidor decide si pide el
-- codigo (deliveryPinRequired) segun el modo de recepcion del pedido, que Orders
-- envia al crear la ruta. Las rutas existentes quedan en NORMAL (sin cambio de comportamiento).

ALTER TABLE internal.delivery_routes
    ADD COLUMN IF NOT EXISTS reception_mode VARCHAR(10) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE internal.delivery_routes
    DROP CONSTRAINT IF EXISTS chk_delivery_routes_reception_mode;

ALTER TABLE internal.delivery_routes
    ADD CONSTRAINT chk_delivery_routes_reception_mode CHECK (reception_mode IN ('NORMAL', 'SEGURO'));
