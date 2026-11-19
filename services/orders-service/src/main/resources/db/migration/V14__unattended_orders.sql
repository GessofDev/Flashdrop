-- F7: pedidos que nadie atiende. Los plazos son globales y los fija el administrador.
--
-- order_policies.courier_wait_minutes: minutos que se espera un repartidor, contados desde que la tienda toma el
-- pedido (al aprobarse el pago). Sin repartidor pasado ese plazo, el pedido se cancela solo.
ALTER TABLE public.order_policies
    ADD COLUMN IF NOT EXISTS courier_wait_minutes integer;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_order_policies_courier_wait') THEN
        ALTER TABLE public.order_policies
            ADD CONSTRAINT chk_order_policies_courier_wait
            CHECK (courier_wait_minutes IS NULL OR courier_wait_minutes BETWEEN 1 AND 1440);
    END IF;
END $$;

UPDATE public.order_policies
   SET courier_wait_minutes = 20
 WHERE restaurant_id IS NULL AND courier_wait_minutes IS NULL;

-- Datos del pedido: cuando la tienda lo tomo, hasta cuando se espera un repartidor, y por que y
-- cuando se cancelo solo. Nullable: los pedidos anteriores no tienen plazo y nunca se cancelan solos.
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS accepted_at   timestamptz,
    ADD COLUMN IF NOT EXISTS courier_due_at timestamptz,
    ADD COLUMN IF NOT EXISTS cancel_reason varchar(40),
    ADD COLUMN IF NOT EXISTS cancelled_at  timestamptz;

-- Soporte de las tareas programadas: solo indexan las filas que les interesan.
CREATE INDEX IF NOT EXISTS idx_orders_courier_due
    ON public.orders(courier_due_at)
    WHERE delivery_id IS NULL AND courier_due_at IS NOT NULL;
