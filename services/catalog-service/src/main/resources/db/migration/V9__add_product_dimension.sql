-- Dimension del producto en una escala de 1 a 10. Orders la suma por pedido para elegir el vehiculo
-- y el costo de envio de la tarifa. Nullable: un producto sin clasificar usa la dimension por defecto de Orders.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS dimension integer;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_dimension') THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_dimension CHECK (dimension IS NULL OR dimension BETWEEN 1 AND 10);
    END IF;
END $$;
