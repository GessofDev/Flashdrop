-- V7__restaurant_rating_stats.sql
-- Cache local de los promedios de calificacion que expone reviews-service.
--
-- reviews-service es el dueno del dato (S9) y catalog-service no lee su base
-- (AGENTS.md §2): guarda aca el ultimo agregado conocido para poder servirlo
-- cuando el pull falla. `count` es el nombre de la columna que fija el
-- contrato de la tabla (design.md §6).

create table public.restaurant_rating_stats (
    restaurant_id bigint primary key,
    average double precision,
    count bigint not null default 0,
    updated_at timestamp with time zone not null default current_timestamp
);

create index idx_restaurant_rating_stats_updated_at on public.restaurant_rating_stats (updated_at);
