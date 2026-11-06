-- =====================================================================
-- V6 - Restaurante sin dueño
--
-- Un restaurante puede existir sin dueño: el administrador lo crea y luego
-- le asigna uno, y al eliminarse la cuenta del dueño se borra su vínculo
-- (dato personal) pero el restaurante y su catálogo se conservan.
-- El UNIQUE se mantiene: varios NULL son válidos en PostgreSQL.
-- =====================================================================

alter table public.restaurant
    alter column user_id drop not null;
