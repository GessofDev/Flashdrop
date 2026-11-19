-- Coordenadas del destino de entrega. Nullable: los pedidos anteriores no las tienen y el
-- envio de respaldo se cobra igual. Se necesitan para calcular la distancia de la tarifa.
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS delivery_latitude  numeric(9,6),
    ADD COLUMN IF NOT EXISTS delivery_longitude numeric(9,6);
