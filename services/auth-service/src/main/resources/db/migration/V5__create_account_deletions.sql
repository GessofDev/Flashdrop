-- =====================================================================
-- V5 - Cola de eliminacion de cuentas (F5)
--
-- Una fila por cuenta en proceso de eliminacion. Auth coordina el borrado en Orders, Delivery y
-- Catalog con reintentos; esta tabla guarda el avance para poder reanudar tras un fallo y no
-- dejar el proceso a medias. Sin FK a users: al final del proceso se borra el usuario y luego esta fila.
-- No guarda datos personales (solo el id numerico del usuario).
-- =====================================================================

create table public.account_deletions (
  user_id         bigint      primary key,
  status          text        not null default 'PENDING',   -- PENDING | FAILED
  attempts        integer     not null default 0,
  orders_done     boolean     not null default false,
  delivery_done   boolean     not null default false,
  catalog_done    boolean     not null default false,
  next_attempt_at timestamptz not null default now(),
  last_error      text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

create index idx_account_deletions_due on public.account_deletions (next_attempt_at) where status = 'PENDING';
