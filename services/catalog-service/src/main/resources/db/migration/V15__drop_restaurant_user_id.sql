-- Phase 4: drop legacy restaurant.user_id column and its index once all consumers use merchant_members.

drop index if exists public.idx_restaurant_user_id;

alter table public.restaurant
    drop column if exists user_id;
