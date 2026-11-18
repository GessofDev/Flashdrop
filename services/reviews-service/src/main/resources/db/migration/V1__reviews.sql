-- ============================================================================
-- V1__reviews.sql — DDL PROPIETARIO EXCLUSIVO de reviews-service
--
-- Base nueva: reviews_db. Esquema: internal (mismo patrón que
-- delivery-service: flyway.schemas=internal + hibernate default_schema=internal).
--
-- Referencias externas (no hay FK posible cross-servicio):
--   order_id        -> orders-service (id numérico público de la orden)
--   author_user_id  -> auth-service (users.id)
--   target_id       -> TIENDA: catalog.restaurant.id
--                      REPARTIDOR / CLIENTE: auth.users.id
--
-- La unicidad (order_id, author_user_id, target_type) es la garantía de
-- idempotencia ante reintentos de red (S6): un reintento devuelve conflicto,
-- no una fila nueva.
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS internal;

CREATE TABLE IF NOT EXISTS internal.reviews (
  id              BIGSERIAL PRIMARY KEY,
  order_id        BIGINT      NOT NULL,          -- id numérico público de la orden
  author_user_id  BIGINT      NOT NULL,          -- auth.users.id
  target_type     VARCHAR(16) NOT NULL,          -- TIENDA | REPARTIDOR | CLIENTE
  target_id       BIGINT      NOT NULL,          -- catalog.restaurant.id | auth.users.id
  rating          SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment         VARCHAR(500),
  status          VARCHAR(16) NOT NULL DEFAULT 'PUBLICADO',  -- PUBLICADO | OCULTO
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_review_participant UNIQUE (order_id, author_user_id, target_type)
);

CREATE INDEX IF NOT EXISTS idx_reviews_target ON internal.reviews (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_reviews_author ON internal.reviews (author_user_id, created_at DESC);
