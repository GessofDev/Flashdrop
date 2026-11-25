alter table public.restaurant
    add column merchant_id bigint references public.merchants(id) on delete restrict;
create index idx_restaurant_merchant on public.restaurant(merchant_id);
