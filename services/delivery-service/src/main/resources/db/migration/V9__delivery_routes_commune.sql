ALTER TABLE internal.delivery_routes
    ADD COLUMN IF NOT EXISTS commune varchar(120);
