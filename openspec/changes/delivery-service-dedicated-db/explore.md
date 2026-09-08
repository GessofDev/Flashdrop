# Explore — `delivery-service-dedicated-db`

> Mirror of engram observation #176 (topic_key `sdd/delivery-service-dedicated-db/explore`).
> Hybrid artifact store: engram is canonical, this file is the local mirror.

## Goal

Extract `services/delivery-service` from the shared Supabase project into a dedicated Postgres database (`delivery_db`) owned by the service, while keeping the existing hexagonal architecture. The `delivery_db` database and `delivery_svc` user are already provisioned at infra level (`infra/coolify/01-postgres-init.sql`); the service is not connected to it yet.

## Territory mapped

| Area | Path | Status |
|---|---|---|
| Hexagonal layout | `services/delivery-service/src/main/java/com/flashdrop/delivery/{domain,application,infrastructure}` | Clean — domain has zero Spring/JPA imports |
| Outbound persistence (current) | `…/infrastructure/adapter/outbound/persistence/supabase/` | Supabase REST only (no JDBC/JPA, no Flyway in build) |
| Outbound HTTP client | `…/infrastructure/adapter/outbound/client/OrderServiceClientAdapter.java` | Queries Supabase orders/restaurant tables directly — not via orders-service |
| Tests | `services/delivery-service/src/test/java/` | 7 files / 19 test methods, mocks at port interfaces |
| Build | `services/delivery-service/build.gradle.kts` | Java 21, Spring Boot 3.3.0; **missing** data-jpa, flyway-core, flyway-database-postgresql, postgresql driver |
| Config | `services/delivery-service/src/main/resources/application-supabase.yml` | Current Supabase env contract |
| Shared observability | `services/shared-observability/` | Has `CorrelationIdFilter`, `ApiError`, `ErrorCatalog`, `TraceContext` — **no InternalApiKeyFilter** |
| Reference (SQL/JPA pattern) | `services/catalog-service/` | Uses SQL/JPA but **Flyway disabled**; auth-service runs migrations on its behalf |
| Infra | `infra/coolify/01-postgres-init.sql` | `delivery_db` + `delivery_svc` user + `internal` schema already provisioned |
| Env contract | `infra/coolify/env.shared.template` | `DELIVERY_DB_NAME` present; `DELIVERY_DB_HOST` missing — URL must combine `POSTGRES_HOST:POSTGRES_PORT/DELIVERY_DB_NAME` |
| Gateway routing | `gateway/docker/gateway.yaml` | `/api/delivery` already routes to `delivery-service:8084` — no change needed |

## What the change requires

1. **Build deps**: add `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql`.
2. **Profile**: new `application-delivery.yml` with a dedicated `DataSource` pointing at `delivery_db` as user `delivery_svc`.
3. **Migrations**: two Flyway scripts under `src/main/resources/db/migration` creating `internal.delivery` and `internal.delivery_routes` tables matching the current Supabase row shapes. First migration must use `CREATE TABLE` (not `IF NOT EXISTS`) — schema is empty.
4. **Adapter swap**: replace the two Supabase REST repositories with Spring Data JPA repositories implementing the same outbound ports. The test that targets the Supabase adapter directly (`SupabaseRestRouteRepositoryTest`) needs a SQL counterpart.
5. **Security**: `InternalApiKeyFilter` does **not** exist in `shared-observability` — it must be created as part of this change (and ideally retrofitted onto the gateway pattern).
6. **Domain fix**: `ClaimDeliveryOrdersUseCaseImpl` creates `DeliveryRoute` with a null `id` — JPA ID generation strategy (UUID or BIGINT) must be defined.
7. **OrderServiceClientAdapter decision**: queries `orders` and `restaurant` tables in Supabase directly. Two paths:
   - (A) Keep reading Supabase for those tables only (pragmatic, smaller change)
   - (B) Refactor to call `orders-service` over HTTP (cleaner but expands scope)
8. **Env contract**: update `env.shared.template` to add `DELIVERY_DB_HOST` (or document how the URL is composed) and a `DELIVERY_SPRING_PROFILES_ACTIVE=delivery` line.

## Risks (ordered by severity)

1. **`OrderServiceClientAdapter` reads `orders`/`restaurant` from Supabase** — if the orders-service migration also moves those tables, this adapter breaks. Decide between (A) keep Supabase for cross-service reads or (B) refactor to HTTP now. **Defer to design phase**.
2. **`InternalApiKeyFilter` does not exist** — must be designed and added to `shared-observability` in this change, with a clear contract (header name, constant-time comparison, error response shape).
3. **`OrderRow.clientId` mapping already broken** — column `client_id` does not exist in `orders`. Either fix the SQL select or drop the field. **Open question for design**.
4. **Flyway discipline** — catalog-service runs Flyway disabled because auth-service handles migrations. delivery-service must own its own migrations (different DB, no shared authority).
5. **Test migration cost** — 7 files / 19 methods are mostly safe because they mock at ports, but the direct Supabase adapter test needs replacement.
6. **Datasource URL composition** — env template lacks `DELIVERY_DB_HOST`; URL must be assembled from `POSTGRES_HOST:POSTGRES_PORT/DELIVERY_DB_NAME` in config or in env.
7. **JPA ID generation** — `ClaimDeliveryOrdersUseCaseImpl` passes null IDs; strategy (UUID @GeneratedValue vs explicit assignment) must be consistent with the rest of the codebase.
8. **First-migration table shape** — internal schema is empty; `CREATE TABLE IF NOT EXISTS` will silently no-op; use plain `CREATE TABLE`.

## Next phase

`sdd-propose` — formalize intent, scope boundaries, and out-of-scope items.