-- =====================================================================
-- FlashDrop Backend — Init script para PostgreSQL en Coolify
-- =====================================================================
-- Ejecutar UNA SOLA VEZ en el primer arranque del container Postgres.
-- Crea 4 bases de datos independientes (una por microservicio) con
-- aislamiento por usuario (least-privilege).
--
-- Cómo se ejecuta en Coolify:
--   1. New Resource → Database → PostgreSQL 16
--   2. Nombre del recurso: flashdrop-postgres
--   3. En "Post Init Commands" o montando este archivo como
--      /docker-entrypoint-initdb.d/01-init.sql dentro del container,
--      pegar el contenido de este archivo.
--   4. Las credenciales de postgres (POSTGRES_USER / POSTGRES_PASSWORD)
--      las genera Coolify automáticamente. Guardarlas.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Crear las 4 bases de datos, una por servicio
-- ---------------------------------------------------------------------
-- Convención de nombres: <servicio>_db
-- Mapeo con el plan de migración:
--   auth_db      → Auth Service    (Nicolás)  — puerto 8081
--   catalog_db   → Catalog Service (Javier)   — puerto 8082
--   orders_db    → Orders Service  (Felipe)   — puerto 8083
--   delivery_db  → Delivery Service (Sebastián) — puerto 8084
-- ---------------------------------------------------------------------

CREATE DATABASE auth_db;
CREATE DATABASE catalog_db;
CREATE DATABASE orders_db;
CREATE DATABASE delivery_db;

-- ---------------------------------------------------------------------
-- 2. Crear un usuario por servicio con permisos restringidos a su BD
-- ---------------------------------------------------------------------
-- Esto es least-privilege: cada servicio se conecta con un usuario que
-- solo puede tocar su propia base. Si en el futuro se decide separar
-- las BDs físicamente (Fase 3 del plan), el aislamiento ya está
-- implementado a nivel de credenciales.
--
-- IMPORTANTE: Reemplazar las contraseñas antes de ejecutar. Coolify
-- también autogenera la password del superusuario (POSTGRES_PASSWORD);
-- guardarla aparte para conectarse a las BDs como admin.
-- ---------------------------------------------------------------------

CREATE USER auth_svc     WITH PASSWORD 'REEMPLAZAR_AUTH_PASS';
CREATE USER catalog_svc  WITH PASSWORD 'REEMPLAZAR_CATALOG_PASS';
CREATE USER orders_svc   WITH PASSWORD 'REEMPLAZAR_ORDERS_PASS';
CREATE USER delivery_svc WITH PASSWORD 'REEMPLAZAR_DELIVERY_PASS';

-- Otorgar ownership de cada BD a su usuario de servicio
GRANT ALL PRIVILEGES ON DATABASE auth_db     TO auth_svc;
GRANT ALL PRIVILEGES ON DATABASE catalog_db  TO catalog_svc;
GRANT ALL PRIVILEGES ON DATABASE orders_db   TO orders_svc;
GRANT ALL PRIVILEGES ON DATABASE delivery_db TO delivery_svc;

-- ---------------------------------------------------------------------
-- 3. Crear el esquema "internal" en cada BD
-- ---------------------------------------------------------------------
-- Convención usada para:
--   - Tablas de uso interno del servicio (no expuestas vía API pública)
--   - Migraciones que el servicio necesita
--
-- Si el plan de migración crea tablas con prefijo "internal_*", este
-- es el esquema donde deben vivir. La Fase 3 del plan (separar BDs)
-- mantiene esta convención.
-- ---------------------------------------------------------------------

\c auth_db
CREATE SCHEMA IF NOT EXISTS internal AUTHORIZATION auth_svc;
ALTER ROLE auth_svc IN DATABASE auth_db SET search_path TO internal, public;

\c catalog_db
CREATE SCHEMA IF NOT EXISTS internal AUTHORIZATION catalog_svc;
ALTER ROLE catalog_svc IN DATABASE catalog_db SET search_path TO internal, public;

\c orders_db
CREATE SCHEMA IF NOT EXISTS internal AUTHORIZATION orders_svc;
ALTER ROLE orders_svc IN DATABASE orders_db SET search_path TO internal, public;

\c delivery_db
CREATE SCHEMA IF NOT EXISTS internal AUTHORIZATION delivery_svc;
ALTER ROLE delivery_svc IN DATABASE delivery_db SET search_path TO internal, public;

-- ---------------------------------------------------------------------
-- 4. Verificación rápida
-- ---------------------------------------------------------------------
-- Después de ejecutar, podés correr:
--   \l                          — lista las 4 bases creadas
--   \du                         — lista los 4 usuarios creados
--   SELECT datname FROM pg_database WHERE datname LIKE '%_db';
--   SELECT usename FROM pg_user WHERE usename LIKE '%_svc';
-- ---------------------------------------------------------------------

\echo '======================================================'
\echo 'FlashDrop init: 4 bases y 4 usuarios creados.'
\echo 'Esquema "internal" configurado en cada BD.'
\echo 'Recordá reemplazar las passwords antes de deploy.'
\echo '======================================================'
