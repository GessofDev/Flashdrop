ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS commune varchar(120);
