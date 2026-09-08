# Design: `delivery-service-dedicated-db`

## Technical Approach

Wire `delivery-service` to its already-provisioned `delivery_db` (PostgreSQL) by adding Spring Data JPA + Flyway + Postgres driver, creating a `delivery` Spring profile with a dedicated `DataSource` against user `delivery_svc`, owning two Flyway migrations in the `internal` schema (`V1__create-tables.sql` builds `internal.delivery`, `internal.delivery_routes`, `internal.delivery_persons`; `V2__seed-delivery_persons.sql` idempotently seeds one `delivery_persons` row), and replacing the two Supabase REST outbound adapters with JPA-backed implementations of the same ports. Concurrently add `InternalApiKeyFilter` in `shared-observability` (auto-registered) so every service authenticates internal traffic on `/api/internal/*`, and expose the new internal endpoints via two dedicated controllers (`InternalDeliveryPersonsController`, `InternalRoutesController`). Public REST contract is unchanged.

## Architecture Overview

```
                    ┌──────────────────────────────────────────────┐
                    │          Fastify Gateway (:3000)             │
                    │   routes /api/delivery -> delivery:8084     │
                    └──────────────────────┬───────────────────────┘
                                           │ X-Internal-Api-Key
                                           ▼
   ┌────────────────────────────────────────────────────────────────────┐
   │                    delivery-service (:8084)                        │
   │  ┌──────────────────────────────────────────────────────────────┐  │
   │  │ infrastructure / adapter / inbound / rest                     │  │
   │  │   RouteController, DeliveryController  (ApiResponse wrapper)  │  │
   │  └────────────────────┬─────────────────────────────────────────┘  │
   │                       │                                            │
   │  ┌────────────────────▼─────────────────────────────────────────┐  │
   │  │ application / port / inbound  (use cases)                     │  │
   │  │ application / port / outbound (RouteRepositoryPort,           │  │
   │  │   DeliveryPersonRepositoryPort, OrderServicePort)            │  │
   │  └──────┬───────────────────────────┬───────────────────────────┘  │
   │         │                           │                              │
   │  ┌──────▼─────────────────┐  ┌───────▼────────────────────────┐    │
   │  │ adapter/outbound/      │  │ adapter/outbound/client/        │   │
   │  │   persistence/jpa/     │  │   OrderServiceClientAdapter     │   │
   │  │   JpaRouteRepository   │  │   (Supabase shared tables -     │   │
   │  │   JpaDeliveryPerson…   │  │    pending DEC-1 follow-up)     │   │
   │  └─────────┬──────────────┘  └──────────────┬───────────────────┘   │
   │            │                                │                       │
   │  ┌─────────▼──────────────┐    ┌────────────▼────────────────────┐  │
   │  │ Flyway migrations       │   │ shared-observability            │  │
   │  │   V1__create-tables.sql  │   │   CorrelationIdFilter           │  │
   │  │     (delivery, routes,   │   │   InternalApiKeyFilter  (NEW)   │  │
   │  │      delivery_persons)   │   │   ApiError, ErrorCatalog        │  │
   │  │   V2__seed-delivery_…    │   │   TraceContext                   │  │
   │  └─────────┬────────────────┘   └────────────────────────────────┘  │
   └────────────┼───────────────────────────────────────────────────────┘
                │ jdbc:postgresql://${DELIVERY_DB_HOST}:5432/delivery_db
                ▼
        ┌───────────────────────┐
        │   PostgreSQL 16       │
        │   delivery_db         │
        │   schema: internal    │
        │   owner: delivery_svc │
        └───────────────────────┘
```

**Layering (hexagonal):** `domain/` and `application/` keep zero framework imports. JPA, Flyway, and `RestClient` live exclusively under `infrastructure/`. The `OrderServicePort` boundary is preserved (the existing adapter remains the implementation; only DEC-1 may change it later).

**Public vs internal API surface:** Public routes (`/api/delivery/*`, `/delivery/*`) keep their current behavior — JWT-gated as the gateway already does — and the response envelope (`ApiResponse`, `code`) is byte-identical. The new `InternalApiKeyFilter` enforces `X-Internal-Api-Key` only on `/api/internal/*`. The internal surface itself (`/api/internal/delivery-persons`, `/api/internal/routes`, `/api/internal/routes/{orderId}/status`) is served by two new controllers — `InternalDeliveryPersonsController` and `InternalRoutesController` — colocated under `infrastructure/adapter/inbound/rest/` and backed by the existing ports (`DeliveryPersonRepositoryPort`, `RouteRepositoryPort`); no domain logic changes. `InternalApiKeyFilter` is auto-registered for every service that depends on `shared-observability`.

## Architecture Decisions

### ADR-1 — `OrderServiceClientAdapter` cross-service strategy

**Choice:** (A) Keep the Supabase-REST adapter for `orders` and `restaurant` reads in this change; add a follow-up ticket to move to (B) HTTP calls when `orders-service` exposes endpoints.
**Alternatives considered:** (B) Refactor to HTTP — true isolation but requires `orders-service` to expose per-method endpoints; out of scope per proposal §7.
**Rationale:** The whole change already extends hexagonal purity by isolating `delivery_db`. The remaining cross-service smell is acceptable as tech debt if tracked explicitly, and (B) is blocked on out-of-scope work. ADR records this debt.
**Consequences (+):** PR stays bounded; no orders-service dependency; unit tests unchanged.
**Consequences (−):** Delivery-service still queries shared tables; if `orders` schema changes, delivery breaks.
**Rollback:** Revert PR; no DB impact (delivery_db is new).

### ADR-2 — `OrderRow.clientId` resolution

**Choice:** (C) Fix the PostgREST select — drop `client_id` from the `select=...` string AND remove the unused `clientId` field from `OrderRow`. (Combined with A's intent.)
**Alternatives:** (A) Drop only; (B) Add column to `orders` (wrong service owns it; rejected).
**Rationale:** `clientId` is never read anywhere in `OrderServiceClientAdapter.toOrderInfo(...)`. The current select asks Supabase for a column that doesn't exist, breaking every call. Removing the field and the select token is a one-line, behavior-preserving fix.
**Consequences (+):** Adapter stops throwing; no schema change to `orders`; no DTO change to `OrderInfo`.
**Consequences (−):** If a future caller needs client identity on an order, the field must be re-added with a proper column.
**Rollback:** Revert; no DB impact.

### ADR-3 — JPA ID strategy

**Choice:** (A) `BIGINT` with `@GeneratedValue(strategy = IDENTITY)`.
**Alternatives:** (B) UUID — requires backfill on existing data; (C) application-side assignment — discards Spring magic.
**Rationale:** The Supabase schema columns are already `BIGINT`; ADR-002 from the prior archive (`consolidate-delivery-into-delivery-service`) explicitly confirmed UUID encoding is not needed. IDENTITY matches both the data shape and what `services/catalog-service` already does.
**Consequences (+):** Matches existing data; matches catalog-service reference; `ClaimDeliveryOrdersUseCaseImpl.createRouteForOrder` (currently passes `null` ID) gets a non-null persisted ID back from `save(...)`.
**Consequences (−):** Multi-instance inserts contend on the sequence — acceptable for delivery-service traffic levels.
**Rollback:** Revert PR; flywayClean in non-prod.

### ADR-4 — `InternalApiKeyFilter` contract

**Choice:** Header `X-Internal-Api-Key`; constant-time compare via `MessageDigest.isEqual`; failure returns HTTP **401** with body `ApiError(code="UNAUTHORIZED", service="<svc>", traceId=<corrId>, message="Missing or invalid internal API key")`; URL pattern `/api/internal/*`; filter ordering after `CorrelationIdFilter` (HIGHEST_PRECEDENCE) and before any service-level JWT filter; registered as a `@Bean FilterRegistrationBean<InternalApiKeyFilter>` inside `ObservabilityAutoConfiguration`; activated when `INTERNAL_API_KEY` env is present; **disabled in `dev` profile** when env is absent (fail-open); **fail-closed** in any non-`dev` profile.
**Rationale:** Matches `ApiError` record already shipped in `shared-observability` (`code`, `service`, `traceId`, `message`). 401 (not 403) because the absence of a credential is an authentication failure, not an authorization one. Auto-configuration guarantees uniform adoption.
**Consequences (+):** Every service that depends on `shared-observability` (auth, catalog, orders, delivery, juniors) picks up the filter on next rebuild; existing `/api/delivery/*` calls are unaffected.
**Consequences (−):** Other services must not start sending the header on `/api/internal/*` paths until they are upgraded; gateway does NOT need it today (only `/api/delivery` is routed).
**Rollback:** Remove the bean from `ObservabilityAutoConfiguration`; revert PR.

### ADR-5 — Java toolchain override (overturns archived ADR-005)

**Choice:** **(B) Stay on Java 21** — `services/delivery-service/build.gradle.kts` lines 11–12 set `sourceCompatibility = JavaVersion.VERSION_21` and `targetCompatibility = JavaVersion.VERSION_21`. The archived ADR-005 from `consolidate-delivery-into-delivery-service` locked Java 17 as an env constraint, but that decision has been overtaken by subsequent work; we explicitly overturn it here with concrete evidence.
**Alternatives considered:** (A) Revert `build.gradle.kts` to `JavaVersion.VERSION_17` — would respect ADR-005 but requires every contributor and CI agent to install a JDK 17 toolchain while the rest of the active monorepo (juniors/* also on Java 21, sdd-init stack listing Java 21) targets JDK 21.
**Rationale / evidence:**
1. **Current repo state is Java 21.** `services/delivery-service/build.gradle.kts` lines 11–12 explicitly declare `JavaVersion.VERSION_21`; the file shipped this way at the time of the prior archive (Task-5 working-tree-only status masked the toolchain drift).
2. **sdd-init/flashdrop_backend** lists `delivery-service — Java 21/Spring Boot 3.3.0`. The detection run on 2026-07-29 confirmed Java 21 as the canonical toolchain for the new delivery-service.
3. **juniors/junior-1-auth** (the active auth-service variant) is also Java 21 per sdd-init; downgrading delivery-service to 17 would create a toolchain split inside the monorepo and break cross-service compile-cache parity.
4. **Spring Boot 3.3.0** supports both JDK 17 and JDK 21; no runtime feature in this design requires JDK 17-specific bytecode.
**Consequences (+):** Aligns design with the live repo state and sdd-init; no env rework; one toolchain across juniors and services.
**Consequences (−):** ADR-005 from the prior archive is now superseded. We explicitly retire it; the only consumers were the prior design's rationale prose and the JDK installer scripts in juniors (which already target 21).
**Rollback:** Revert PR; `git checkout` ADR-005 in any future reference doc if resurrected.

## Data Flow

```
POST /api/delivery/claim
  └─▶ RouteController (inbound REST, ApiResponse)
        └─▶ ClaimDeliveryOrdersUseCaseImpl (application)
              ├─▶ OrderServicePort.findOrdersByIds
              │     └─▶ OrderServiceClientAdapter → Supabase /orders, /restaurant  (DEC-1=A)
              └─▶ RouteRepositoryPort.save
                    └─▶ JpaRouteRepository (infrastructure/jpa)
                          └─▶ Hibernate INSERT INTO internal.delivery_routes
                                └─▶ delivery_db (user delivery_svc)
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `services/delivery-service/build.gradle.kts` | Modify | Add `spring-boot-starter-data-jpa`, `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`, runtime `org.postgresql:postgresql`, test `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`. |
| `services/delivery-service/src/main/resources/application-delivery.yml` | Create | `spring.datasource.url=jdbc:postgresql://${DELIVERY_DB_HOST:POSTGRES_HOST}:${DELIVERY_DB_PORT:5432}/${DELIVERY_DB_NAME}`, user/password from env, `jpa.hibernate.ddl-auto=validate`, `flyway.enabled=true`, `flyway.schemas=internal`, `spring.jpa.properties.hibernate.default_schema=internal`. |
| `services/delivery-service/src/main/resources/db/migration/V1__create-tables.sql` | Create | `CREATE TABLE internal.delivery (id BIGSERIAL PRIMARY KEY, code VARCHAR(64) NOT NULL UNIQUE, address VARCHAR(512), status VARCHAR(32) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMPTZ NOT NULL DEFAULT now()); CREATE INDEX idx_delivery_status ON internal.delivery(status); CREATE TABLE internal.delivery_routes (id BIGSERIAL PRIMARY KEY, delivery_id BIGINT NOT NULL REFERENCES internal.delivery(id), order_id VARCHAR(64), delivery_person_id BIGINT, status VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED', created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()); CREATE INDEX idx_routes_delivery_person ON internal.delivery_routes(delivery_person_id); CREATE INDEX idx_routes_status ON internal.delivery_routes(status); CREATE TABLE internal.delivery_persons (id BIGSERIAL PRIMARY KEY, user_id VARCHAR(64) NOT NULL UNIQUE, active BOOLEAN NOT NULL DEFAULT true, created_at TIMESTAMPTZ NOT NULL DEFAULT now());` |
| `services/delivery-service/src/main/resources/db/migration/V2__seed-delivery_persons.sql` | Create | Idempotent `INSERT … ON CONFLICT (user_id) DO NOTHING` seeding a single `delivery_persons` row (`user_id='U1'`, `active=true`). FR-5 + Scenario 1 satisfied; no production coupling. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/jpa/DeliveryRouteJpaEntity.java` | Create | JPA entity for `internal.delivery_routes`. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/jpa/DeliveryPersonJpaEntity.java` | Create | JPA entity for `internal.delivery_persons` (`id BIGINT IDENTITY`, `userId VARCHAR(64) UNIQUE`, `active BOOLEAN`). |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/jpa/JpaRouteRepository.java` | Create | Spring Data repo implementing `RouteRepositoryPort`. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/jpa/JpaDeliveryPersonRepository.java` | Create | Spring Data repo implementing `DeliveryPersonRepositoryPort`. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/jpa/PersistenceConfig.java` | Create | `@EnableJpaRepositories(basePackages=…)`, `@EntityScan`, profile guard. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/supabase/SupabaseRestRouteRepository.java` | Delete | Replaced by `JpaRouteRepository`. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/supabase/SupabaseRestDeliveryPersonRepository.java` | Delete | Replaced by `JpaDeliveryPersonRepository`. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/supabase/OrderRow.java` | Modify | Drop unused `clientId` field (DEC-2). |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/supabase/DeliveryRouteRow.java` | Delete | Consumed only by the deleted `SupabaseRestRouteRepository`. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/supabase/DeliveryRow.java` | Delete | Same. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/supabase/RestaurantRow.java` | Keep | Still used by `OrderServiceClientAdapter`. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/client/OrderServiceClientAdapter.java` | Modify | Remove `client_id` from PostgREST select (DEC-2). |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/inbound/rest/RouteController.java` | Keep | No public-contract change. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/inbound/rest/DeliveryController.java` | Keep | No public-contract change. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/inbound/rest/InternalDeliveryPersonsController.java` | Create | New internal REST controller: `@RequestMapping("/api/internal/delivery-persons")`. Exposes `GET ?userId=…` (Scenario 6), backed by `DeliveryPersonRepositoryPort.findByUserId(...)`. Protected by `InternalApiKeyFilter`. Returns `ApiResponse<DeliveryPerson>`. |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/inbound/rest/InternalRoutesController.java` | Create | New internal REST controller: `@RequestMapping("/api/internal/routes")`. Exposes `POST /` (Scenario 7, `CreateDeliveryRouteRequest` body) and `PATCH /{orderId}/status` (Scenario 9). Backed by `RouteRepositoryPort` via the existing use cases (`ClaimDeliveryOrdersUseCaseImpl`, `UpdateRouteStatusUseCaseImpl`). Protected by `InternalApiKeyFilter`. |
| `services/delivery-service/src/main/java/.../infrastructure/config/InternalApiKeyFilterRegistration.java` | Create | `@Profile("!dev")` bean wiring `InternalApiKeyFilter` for delivery-service explicitly (in addition to the auto-config). |
| `services/delivery-service/src/test/java/.../infrastructure/adapter/inbound/rest/InternalDeliveryPersonsControllerTest.java` | Create | `@WebMvcTest` slice covering: missing key → 401 `ApiError`, wrong key → 401 `ApiError`, correct key + seeded user → 200 `ApiResponse<DeliveryPerson>` with matching `id`/`userId` (Scenarios 4/5/6). |
| `services/delivery-service/src/test/java/.../infrastructure/adapter/inbound/rest/InternalRoutesControllerTest.java` | Create | `@WebMvcTest` slice covering: POST valid body → 201 + DB insert (Scenario 7); POST missing required field → 400 `ApiError` (Scenario 8); PATCH `{orderId}/status` → 200 + row updated (Scenario 9). |
| `services/delivery-service/src/test/java/.../infrastructure/adapter/outbound/persistence/supabase/SupabaseRestRouteRepositoryTest.java` | Delete | Replaced by SQL counterpart. |
| `services/delivery-service/src/test/java/.../infrastructure/adapter/outbound/persistence/jpa/JpaRouteRepositoryIT.java` | Create | Testcontainers Postgres + Flyway; CRUD round-trip. |
| `services/delivery-service/src/test/java/.../infrastructure/adapter/outbound/persistence/jpa/JpaDeliveryPersonRepositoryIT.java` | Create | Testcontainers counterpart. |
| `services/delivery-service/src/test/java/.../DeliveryServiceApplicationIT.java` | Create | `@SpringBootTest` boot test asserting Flyway migrations succeed on an empty Testcontainer `delivery_db`. |
| `services/shared-observability/build.gradle.kts` | Modify | Add `spring-boot-starter-web` if not present (for `OncePerRequestFilter`). |
| `services/shared-observability/src/main/java/.../security/InternalApiKeyFilter.java` | Create | `OncePerRequestFilter`; reads `INTERNAL_API_KEY` env (or constructor-injected value); compares with `MessageDigest.isEqual`; on mismatch writes `ApiError` JSON with 401. |
| `services/shared-observability/src/main/java/.../config/ObservabilityAutoConfiguration.java` | Modify | Add `@Bean FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilter(@Value("${internal.api.key:#{null}}") String key)`. Profile guard: only when env present and profile ≠ `dev`. |
| `services/shared-observability/src/test/java/.../security/InternalApiKeyFilterTest.java` | Create | `MockMvc` against a minimal `@WebMvcTest`; assert 401 body shape and 200 on correct key. |
| `infra/coolify/env.shared.template` | Modify | `DELIVERY_DB_NAME=delivery_db`, `DELIVERY_DB_USER=delivery_svc`, `DELIVERY_DB_PASSWORD=…` already present (lines 123–125, verified). Add `DELIVERY_DB_HOST=${POSTGRES_HOST}` and `DELIVERY_DB_PORT=${POSTGRES_PORT}` defaults; document `SPRING_PROFILES_ACTIVE=delivery` for delivery service. `INTERNAL_API_KEY` already declared at line 64. |
| `README.md` | Modify | Add one sentence: "delivery-service now reads/writes its own `delivery_db` via Spring Data JPA + Flyway." |
| `gateway/CLAUDE.md` | Modify | Note that `/api/delivery` is SQL-backed; `delivery_db` env host/PORT are required. |

## Migration / Seed

- `V1__create-tables.sql` — single migration that creates all three tables in `internal` schema:
  - `internal.delivery` — `id BIGSERIAL PK`, `code UNIQUE`, `address`, `status default 'PENDING'`, `created_at default now()`, plus `idx_delivery_status`.
  - `internal.delivery_routes` — `id BIGSERIAL PK`, FK to `internal.delivery(id)`, `order_id VARCHAR(64)` (Scenarios 7/9 key field), nullable `delivery_person_id` (FR-3 forward-compat), `status default 'ASSIGNED'`, timestamps, plus `idx_routes_delivery_person`, `idx_routes_status`.
  - `internal.delivery_persons` — `id BIGSERIAL PK`, `user_id VARCHAR(64) UNIQUE`, `active BOOLEAN default true`, `created_at default now()`.
- `V2__seed-delivery_persons.sql` — idempotent `INSERT … ON CONFLICT (user_id) DO NOTHING` for a single `delivery_persons` row (`user_id='U1'`, `active=true`). FR-5 + Scenario 1 satisfied; no production coupling. (Seeded into `delivery_persons`, not `delivery`, per spec Scenario 1: "a `delivery_persons` row is present in the DB".)

Plain `CREATE TABLE` (no `IF NOT EXISTS`) per proposal §10 edge case — schema is empty per `01-postgres-init.sql`; boot test asserts the empty invariant. `delivery_persons` is owned by `delivery_svc`; other services do not reference it.

## Testing Strategy (Strict TDD)

| Layer | What | Where |
|---|---|---|
| Unit | Use cases (3 files, 19 methods preserved) | `…/application/usecase/*Test.java` — keep port mocks; no change |
| Unit | `OrderServiceClientAdapter` post-select fix | `…/outbound/client/OrderServiceClientAdapterTest.java` — assert URL no longer contains `client_id` |
| Unit | `InternalApiKeyFilter` | `…/security/InternalApiKeyFilterTest.java` — correct key → 200, missing → 401 with `ApiError`, wrong key → 401 |
| Architecture | Hexagon purity | `DeliveryArchitectureTest.java` — ArchUnit: forbid `jakarta.persistence`, `org.springframework.data`, `org.springframework.boot` in `domain/` and `application/` (NFR-1) |
| Integration | JPA repo round-trip | `JpaRouteRepositoryIT.java`, `JpaDeliveryPersonRepositoryIT.java` — Testcontainers Postgres 16, Flyway-on, port-mock-free |
| Integration | Boot happy path | `DeliveryServiceApplicationIT.java` — empty container → Flyway runs both V1 (create-tables) + V2 (seed-delivery_persons) → `/actuator/health` UP |
| Migration | BDD-style scenario | `DeliveryMigrationIT.java` — given empty DB, when app boots, then all three tables exist and the seeded `delivery_persons` row for `U1` is present (Scenarios 1, 10) |
| Web slice | Internal controllers | `InternalDeliveryPersonsControllerTest.java`, `InternalRoutesControllerTest.java` — `@WebMvcTest` covering Scenarios 4–9 (missing/wrong/correct key, valid/invalid body, PATCH status) |

Testcontainers plan: one Postgres 16 container per IT class (`@Container static`), reused across tests via `@Testcontainers(parallel = false)`. CI cache key matches juniors (image `postgres:16-alpine`). No JWT seeding needed — filter is tested at the unit layer.

### Test migration mapping

| Existing file | Action |
|---|---|
| `ClaimDeliveryOrdersUseCaseImplTest.java` | Keep — mocks `RouteRepositoryPort`; no change |
| `ListDeliveryRoutesUseCaseImplTest.java` | Keep — port mocks; no change |
| `UpdateRouteStatusUseCaseImplTest.java` | Keep — port mocks; no change |
| `DeliveryControllerTest.java`, `RouteControllerTest.java` | Keep — `@WebMvcTest`; no change |
| `OrderServiceClientAdapterTest.java` | Modify — replace `select` regex to assert `client_id` is gone |
| `SupabaseRestRouteRepositoryTest.java` | Delete — replaced by `JpaRouteRepositoryIT.java` |

## PR / Chaining Plan (auto-chain, stacked-to-main)

| PR | Branch | Scope | Files (≈) | Boundary | Verification |
|---|---|---|---|---|---|
| **PR-1** `feat/shared-observability-internal-api-key` | new | `InternalApiKeyFilter` + auto-config bean + filter unit test + small `env.shared.template` doc lines | ~280 LOC | Independent — adds the filter for every service. No behavior change for delivery routes. | `shared-observability:test`, new `InternalApiKeyFilterTest` green. |
| **PR-2** `feat/delivery-jpa-adapter-swap` | PR-1 | Add JPA/Flyway deps + `application-delivery.yml` + `V1__create-tables.sql` + `V2__seed-delivery_persons.sql` + JPA entities/repos + `PersistenceConfig` + DEC-2 select fix; delete the two Supabase adapters and `DeliveryRouteRow`/`DeliveryRow` | ~395 LOC | Adapter swap + schema migrations ship together — `RouteRepositoryPort` and `DeliveryPersonRepositoryPort` interfaces unchanged. Use-case tests still pass via port mocks. delivery-service becomes self-contained after this PR; `/api/internal/*` controllers are added in PR-3. | `:delivery-service:test`, ArchUnit green, build green. |
| **PR-3** `feat/delivery-internal-controllers` | PR-2 | Add `InternalDeliveryPersonsController` + `InternalRoutesController`; add `InternalDeliveryPersonsControllerTest` + `InternalRoutesControllerTest` (Scenarios 4–9) | ~180 LOC | New internal API surface only. Protected by `InternalApiKeyFilter` shipped in PR-1. No change to public `/api/delivery/*` routes. | `:delivery-service:test --tests '*ControllerTest'` green; manual `curl` against `/api/internal/*` with/without key returns expected status. |
| **PR-4** `chore/delivery-testcontainers-it-and-cleanup` | PR-3 | Replace `SupabaseRestRouteRepositoryTest` with `JpaRouteRepositoryIT` + `JpaDeliveryPersonRepositoryIT` + `DeliveryServiceApplicationIT` + `DeliveryMigrationIT` + arch test; docs touch (`README.md`, `gateway/CLAUDE.md`) | ~280 LOC | Tests + docs only. No production code change. Schema migrations already shipped in PR-2. | `:delivery-service:test --tests '*IT'` green; `flywayClean` clean; `bootJar` works with `SPRING_PROFILES_ACTIVE=delivery`. |

Each PR ≤ 400 changed lines. Branching strategy: `stacked-to-main` (PR-1 → main; PR-2 → main after PR-1; PR-3 → main after PR-2; PR-4 → main after PR-3). Rollback per PR is the git revert — no shared-state contamination because `delivery_db` is owned by no one else. The split into 4 PRs (instead of the originally proposed 3) keeps each PR under the 400-line budget while keeping schema migrations (V1, V2) co-located with the JPA adapter swap in PR-2.

## Risks and Mitigations

**Top 3**

1. **DEC-1 keeps shared-table coupling.** Mitigation: ADR-1 records tech debt; open follow-up Jira ticket `FD-XXX: refactor OrderServiceClientAdapter to orders-service HTTP` gated on orders-service exposing endpoints.
2. **InternalApiKeyFilter adoption gap in other services.** Mitigation: auto-config + module-level unit test + README note; auth/catalog/orders gain the filter passively on next rebuild.
3. **Flyway schema drift on non-empty `delivery_db`.** Mitigation: plain `CREATE TABLE`, boot-time IT asserts schema starts empty, `flywayClean` documented for non-prod rollback.

**Others**

4. Hexagon break via JPA leakage into domain/application — ArchUnit test forbids the imports (NFR-1).
5. `DELIVERY_DB_HOST` missing on deploy — default to `${POSTGRES_HOST}` in `application-delivery.yml`; documented in `env.shared.template` (`DELIVERY_DB_NAME` already present at line 123, verified).
6. IDENTITY sequence contention under burst — acceptable for delivery traffic; UUID backstop in a future migration if it materializes.
7. Testcontainers CI time — cache the container per-class; mirror juniors' pattern.
8. `OrderRow` field removal breaks any external consumer — no external consumer (record is package-internal); safe.
9. **PR-2 budget drift.** Pulling in two new internal controllers pushed PR-2 to ~520 LOC; mitigation: split into a dedicated PR-3 (`feat/delivery-internal-controllers`, ~180 LOC). Schema migrations stay in PR-2 to keep adapter swap self-contained.

## Rollback Plan

- **DB rollback (non-prod):** `./gradlew :delivery-service:flywayClean -Pflyway.schemas=internal` removes the V1 (create-tables) and V2 (seed-delivery_persons) applied state from `delivery_db`. Safe in dev/CI only.
- **DB rollback (production):** Flyway community does **not** ship paired `U*.sql` undo migrations (that feature requires Flyway Teams/Pro license). The clean rollback path is **code-only + manual drop**, executed as a one-off ops script: `psql -U delivery_svc -d delivery_db -c "DROP SCHEMA IF EXISTS internal CASCADE;"` followed by `git revert` of PR-2. No data loss because `delivery_db` was empty before this change. The ops runbook lives alongside `infra/coolify/01-postgres-init.sql` and is documented in PR-2's commit body.
- **Code rollback:** `git revert` the offending PR. PR-2 is the only PR that touches production persistence code; PR-1 (filter), PR-3 (internal controllers), PR-4 (IT + docs) are additive and reversible independently.
- **Filter rollback:** PR-1 revert removes the bean from `ObservabilityAutoConfiguration`; gateway and other services revert to "no internal-key check" (pre-change state).
- **Internal-controller rollback:** PR-3 revert removes `InternalDeliveryPersonsController` and `InternalRoutesController`; PR-1 filter still enforces the 401 on `/api/internal/*` until that is also reverted.
- **No shared-state contamination:** `auth-service`, `catalog-service`, `orders-service` continue to own their respective DBs and migrations; this change exclusively touches `delivery_db`.

## Open Questions

None for the four DEC items. Java toolchain contradiction between archived ADR-005 (Java 17) and live repo state (Java 21) is resolved by **ADR-5** (Java toolchain override) above; archived ADR-005 is explicitly retired. Remaining work is captured as a single follow-up ticket (DEC-1 → B when orders-service exposes HTTP).