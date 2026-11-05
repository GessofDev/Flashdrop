-- =====================================================================
-- V5 - Apertura de los locales
--
-- accepting_orders es el interruptor manual del local. restaurant_hours es
-- el horario semanal: una fila por dia, con day_of_week ISO (1 = lunes).
--
--   - closes_at < opens_at significa que cierra al dia siguiente (22:00-02:00).
--   - Un local sin filas no tiene horario definido y se rige solo por el
--     interruptor: asi los locales existentes siguen recibiendo pedidos.
--   - Un local con filas esta cerrado los dias que no aparecen.
-- =====================================================================
alter table public.restaurant
    add column accepting_orders boolean not null default true;

create table public.restaurant_hours (
    restaurant_id bigint not null references public.restaurant(id) on delete cascade,
    day_of_week smallint not null check (day_of_week between 1 and 7),
    opens_at time not null,
    closes_at time not null,
    primary key (restaurant_id, day_of_week),
    check (opens_at <> closes_at)
);
