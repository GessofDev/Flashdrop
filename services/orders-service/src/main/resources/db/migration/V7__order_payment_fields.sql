-- =====================================================================
-- V7 - Datos de pago en el pedido (FlashDrop 1.0 tiendas, spec 4.1)
--
--   idempotency_key        : llave que envia la app para que reenviar la misma peticion
--                            devuelva el mismo pedido en vez de crear otro.
--   payment_expires_at     : hasta cuando puede pagarse el pedido (estado Pendiente de pago).
--   store_response_due_at  : hasta cuando la tienda debe aceptar el pedido ya pagado. Se
--                            llena al aprobarse el pago, no al crear el pedido.
--
-- El estado nuevo "Pendiente de pago" no necesita DDL: orders.status es texto libre y los
-- valores validos viven en el enum OrderStatus.
-- Los pedidos existentes quedan con los tres campos en NULL y siguen funcionando igual.
-- =====================================================================
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS idempotency_key       varchar(64),
    ADD COLUMN IF NOT EXISTS payment_expires_at    timestamptz,
    ADD COLUMN IF NOT EXISTS store_response_due_at timestamptz;

-- Misma llave del mismo cliente = mismo pedido. Los NULL (pedidos historicos) no chocan.
CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_client_idempotency
    ON public.orders(client_id, idempotency_key);

-- Soporte de las tareas programadas: solo indexan las filas que les interesan.
CREATE INDEX IF NOT EXISTS idx_orders_payment_expiry
    ON public.orders(payment_expires_at) WHERE status = 'Pendiente de pago';
CREATE INDEX IF NOT EXISTS idx_orders_store_response_due
    ON public.orders(store_response_due_at) WHERE status = 'Nuevo pedido';
