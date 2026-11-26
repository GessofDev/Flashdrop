-- Phase 3 (irreversible): drop UNIQUE(user_id) on public.restaurant to allow multiple stores per owner/merchant.
DO $$
DECLARE
    v_duplicate_owners integer;
    v_missing_merchant integer;
BEGIN
    SELECT count(*) INTO v_duplicate_owners
    FROM (
        SELECT user_id
        FROM public.restaurant
        WHERE user_id IS NOT NULL
        GROUP BY user_id
        HAVING count(*) > 1
    ) dup;

    IF v_duplicate_owners > 0 THEN
        RAISE EXCEPTION 'Cannot drop restaurant_user_id_key: % user_id(s) have multiple restaurants without prior merge', v_duplicate_owners;
    END IF;

    SELECT count(*) INTO v_missing_merchant
    FROM public.restaurant
    WHERE user_id IS NOT NULL
      AND merchant_id IS NULL;

    IF v_missing_merchant > 0 THEN
        RAISE EXCEPTION 'Cannot drop restaurant_user_id_key: % owned restaurant(s) have null merchant_id (violates D11)', v_missing_merchant;
    END IF;
END $$;

alter table public.restaurant drop constraint if exists restaurant_user_id_key;
