-- Conserva el producto tal como se ofrecía al crear el pedido. Esto permite
-- consultar el detalle aunque Catalog cambie o deje de exponer ese producto.
ALTER TABLE public.order_items
    ADD COLUMN IF NOT EXISTS product_name text,
    ADD COLUMN IF NOT EXISTS product_description text,
    ADD COLUMN IF NOT EXISTS product_image text;
