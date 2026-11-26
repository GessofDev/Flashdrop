-- Backfill one merchant and one DUEÑO membership per owned restaurant.
-- Iterates per restaurant so distinct owners with homonymous store names
-- are never merged into the same merchant.
DO $$
DECLARE
    rec record;
    v_merchant_id bigint;
BEGIN
    FOR rec IN
        SELECT r.id AS restaurant_id, r.user_id, r.name
        FROM public.restaurant r
        WHERE r.user_id IS NOT NULL
          AND r.merchant_id IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM public.merchant_members mm WHERE mm.user_id = r.user_id
          )
        ORDER BY r.id
    LOOP
        INSERT INTO public.merchants (name)
        VALUES (rec.name)
        RETURNING id INTO v_merchant_id;

        INSERT INTO public.merchant_members (merchant_id, user_id, role)
        VALUES (v_merchant_id, rec.user_id, 'DUEÑO')
        ON CONFLICT (merchant_id, user_id) DO NOTHING;

        UPDATE public.restaurant
        SET merchant_id = v_merchant_id
        WHERE id = rec.restaurant_id;
    END LOOP;
END $$;
