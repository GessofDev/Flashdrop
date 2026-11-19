-- El pedido nace "Pendiente de pago" y su ruta de entrega se crea recien al aprobarse el pago.
-- Para no perder lo que la app envio al crearlo (o lo que calculo la tarifa), la distancia y el
-- tiempo estimado se guardan en el pedido. Nullable: los pedidos anteriores usan los valores por defecto.
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS distance_km         numeric(6,2),
    ADD COLUMN IF NOT EXISTS estimated_minutes   integer;
