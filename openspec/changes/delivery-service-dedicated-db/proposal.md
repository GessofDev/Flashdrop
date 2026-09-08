# Proposal: `delivery-service-dedicated-db`

## 1. Problem statement

`flashdrop_backend` is a microservices monorepo whose README architecture requires **each microservice to own its own database**. The `delivery-service` (canonical, port 8084) is the only service still reading through the shared Supabase project, violating that boundary. The `delivery_db` database and `delivery_svc` user are already provisioned at infra level (`infra/coolify/01-postgres-init.sql`); the service is simply not connected to them yet. Until this is fixed:

- The service has no transactional ownership of its data, no Flyway migrations, and no schema evolution path.
- Cross-service data reads happen by hitting shared Supabase tables (`orders`, `restaurant`) from inside `delivery-service` — a clear architectural smell.
- The `shared-observability` module has no `InternalApiKeyFilter`, so service-to-service trust has no shared mechanism.

This change wires the service to its own DB while preserving the hexagonal architecture that the prior archive (`consolidate-delivery-into-delivery-service`) deliberately hardened.

## 2. Target users / situations

**Internal callers only**: the Fastify gateway (already routing `/api/delivery` → `delivery-service:8084` per archive report) and other Java services that may in the future call delivery over HTTP. There is **no end-user-facing change** — same routes, same `ApiResponse` envelope, same `code` field semantics.

## 3. Business rules / constraints

| Rule | Source |
|---|---|
| Each microservice owns its DB | `README.md` architecture |
| `delivery_svc` is a least-privilege user (no superuser) | `01-postgres-init.sql` |
| Internal calls must carry `X-Internal-Api-Key` | New filter contract |
| Hexagonal purity — no Spring/JPA in `domain/` | Existing convention |
| Strict TDD — Testcontainers-backed integration tests | `sdd-init/flashdrop_backend` |
| `delivery-service` must own its own Flyway migrations (no shared authority) | Explore risk #4 |
| Java 17 toolchain (env constraint) | Archived ADR-005 |

## 4. Product outcome / acceptance

- `delivery-service` connects to `delivery_db` on boot, runs Flyway migrations, and exposes the same REST contract.
- Two `delivery_routes` records created via `POST /api/delivery/claim` round-trip through JPA and read back identically via `GET /api/delivery/routes`.
- The `X-Internal-Api-Key` header is enforced on every endpoint via a filter in `shared-observability`; missing/wrong key returns **401** with `ApiError` shape.
- All 19 existing unit tests stay green; a new Testcontainers-backed integration test covers the JPA adapter.

## 5. Current-state gap (concrete evidence from explore #176)

| Gap | Evidence |
|---|---|
| Service uses Supabase REST, not its own DB | `SupabaseRestRouteRepository.java`, `SupabaseRestDeliveryPersonRepository.java` |
| No Flyway, no JPA in `build.gradle.kts` | Explore territory map |
| `OrderRow.clientId` mapping broken | `OrderRow.java` selects `client_id`; no such column in `orders` |
| `OrderServiceClientAdapter` reads shared `orders` + `restaurant` tables directly | `OrderServiceClientAdapter.java` lines 33-34, 73-74 |
| `ClaimDeliveryOrdersUseCaseImpl.createRouteForOrder` passes `null` ID | `ClaimDeliveryOrdersUseCaseImpl.java` line 84 |
| `InternalApiKeyFilter` absent in `shared-observability` | Glob: only `CorrelationIdFilter`, `ApiError`, `ErrorCatalog`, `TraceContext` |
| `delivery_db` + `delivery_svc` provisioned but unused | `01-postgres-init.sql` |
| Catalog-service precedent runs Flyway disabled | Explore risk #4 (anti-pattern to avoid) |
| FR-3 from previous change (`deliveryPersonId` filter) still WARNING | Archive-report open items |

## 6. Scope (in)

1. Add Spring Data JPA + Flyway + PostgreSQL driver to `delivery-service/build.gradle.kts`.
2. New `application-delivery.yml` profile with a dedicated `DataSource` for `delivery_db` as user `delivery_svc`.
3. Two Flyway migrations under `src/main/resources/db/migration/` creating `internal.delivery` and `internal.delivery_routes` (plain `CREATE TABLE` — schema is empty).
4. New JPA entities + Spring Data repositories implementing the existing outbound ports (`RouteRepository`, `DeliveryPersonRepository`).
5. `OrderServicePort` keeps its current REST-based implementation but reads `orders`/`restaurant` via the new shared orders-service boundary (see decision §8).
6. New `InternalApiKeyFilter` in `services/shared-observability/` registered on every protected route of every service.
7. Env contract: `env.shared.template` adds `DELIVERY_DB_HOST` and `DELIVERY_SPRING_PROFILES_ACTIVE=delivery`.
8. Replace `SupabaseRestRouteRepositoryTest` with a Testcontainers-backed SQL integration test.
9. Boot-time integration test asserting Flyway runs and the service starts against an empty `delivery_db`.

## 7. Non-goals

- Moving `orders-service` or `catalog-service` tables to dedicated DBs.
- Refactoring the gateway routes or `gateway.yaml`.
- Cleaning up `juniors/` or `references/` directories.
- Implementing FR-3 (`deliveryPersonId` filter) — still deferred; see Out-of-scope follow-ups.
- Changing public REST contracts (paths, body shape, `ApiResponse` envelope).
- Switching delivery-service from Java 17 to Java 21 (env constraint).
- Replacing `OrderServiceClientAdapter` with an HTTP call to orders-service — see open decision §8.

## 8. Open decisions (to be resolved at design phase)

### 8.1 `OrderServiceClientAdapter` cross-service reads

| Option | Tradeoff |
|---|---|
| **(A) Keep reading Supabase for `orders`/`restaurant`** | Smaller blast radius; delivery-service still depends on shared DB even though it owns `delivery_db`. Tables `orders`/`restaurant` live wherever orders-service puts them. |
| **(B) Refactor to call orders-service over HTTP** | True service isolation; new failure mode (network); requires orders-service to expose an HTTP endpoint per `OrderServicePort` method; scope grows. |

### 8.2 `OrderRow.clientId` resolution

| Option | Tradeoff |
|---|---|
| **(A) Drop the field** — orders don't belong to clients, they belong to users. | Removes the bug; requires updating `OrderInfo` DTO and any consumer. |
| **(B) Add a `client_id` column to `orders`** | Preserves API surface but commits to a schema change that should belong to orders-service, not delivery-service. |
| **(C) Fix the PostgREST select** to not request `client_id` | Smallest code change; treats the missing column as already-known and stops selecting it. |

### 8.3 JPA ID strategy

| Option | Tradeoff |
|---|---|
| **(A) `BIGINT` with `@GeneratedValue(strategy = IDENTITY)`** | Matches the actual Supabase schema (BIGINT columns); Java 17 friendly; matches `services/orders-service` reference. |
| **(B) UUID with `@GeneratedValue`** | Globally unique; no sequence contention; mismatch with existing BIGINT columns means a backfill column would be needed. |
| **(C) Explicit application-side assignment** | Deterministic; awkward to test; no Spring magic. |

**Recommendation surface**: prefer (A) unless a design reviewer finds a forcing function for (B).

### 8.4 `InternalApiKeyFilter` contract

| Question | Options |
|---|---|
| Module | `services/shared-observability/src/main/java/com/flashdrop/observability/security/` (new package) |
| Header | `X-Internal-Api-Key` (constant) |
| Comparison | `MessageDigest.isEqual` (constant-time) |
| Failure | HTTP **401** with `ApiError(code="UNAUTHORIZED", message="Missing or invalid internal API key")` |
| Filter order | Before auth filters, after `CorrelationIdFilter` |
| Activation | Auto-configured bean in `ObservabilityAutoConfiguration` so every service picks it up |
| Key source | `INTERNAL_API_KEY` env var; absence of env = filter disabled in `dev` profile only |

## 9. Implications and impact

- **Gateway**: zero change — same routes, same target `delivery-service:8084`.
- **orders-service**: no change to its public surface. If §8.1 lands on (B), orders-service must expose HTTP endpoints; that work is explicitly out of scope here.
- **auth-service / catalog-service**: no functional change. They will, however, gain `InternalApiKeyFilter` automatically via the shared module auto-config when next rebuilt.
- **CI / docker-compose**: `stack-compose.yml` needs a `DELIVERY_DB_HOST` env and the `delivery` profile enabled; no new container (database already exists).
- **Observability**: same Prometheus + correlation-id behavior; new filter logs auth failures at WARN.

## 10. Edge cases

| Edge case | Handling |
|---|---|
| DB unreachable at boot | Spring Boot's default health check fails; container restarts; log clearly identifies `delivery_db` connection error |
| Missing `INTERNAL_API_KEY` env in production | Filter still enforces, comparing against empty → all calls 401 (fail-closed) |
| Missing `INTERNAL_API_KEY` in `dev` profile | Filter disabled (explicit opt-out) |
| Flyway runs against a non-empty schema | First migration uses plain `CREATE TABLE` (no `IF NOT EXISTS`); schema is currently empty per infra |
| Concurrent service instances racing migrations | Flyway's `flyway_schema_history` lock handles this; tested via Testcontainers |
| Duplicate claim (same `orderIds`) during migration window | `RouteAlreadyAssignedException` already enforced by `validateOrders`; behavior unchanged |
| `OrderServiceClientAdapter` returns empty when orders-service is down | Existing code returns `List.of()`; `validateOrders` then throws `IllegalArgumentException("Some orders were not found")` — preserved |

## 11. Risks

| # | Risk | Likelihood | Mitigation |
|---|---|---|---|
| 1 | `OrderServiceClientAdapter` keeps cross-service DB coupling (if §8.1 = A) | Med | Add ADR noting debt; create follow-up ticket to refactor when orders-service exposes HTTP |
| 2 | `InternalApiKeyFilter` contract not adopted by other services | Med | Auto-configure in `shared-observability`; add module-level unit test that loads the bean |
| 3 | Flyway schema drift if `delivery_db` is not empty | Low | Plain `CREATE TABLE`; boot test asserts schema starts empty in CI |
| 4 | JPA lazy-loading leaks into controllers (hexagon break) | Med | Keep repositories as the only place `EntityManager` is used; controllers receive DTOs only; add architecture test forbidding `jakarta.persistence` in `domain/` and `application/` |
| 5 | Testcontainers startup adds CI time | Low | Reuse the existing pattern from `juniors/junior-1-auth`; cache container across tests |
| 6 | `DELIVERY_DB_HOST` env var absent on deploy | Med | Default to `POSTGRES_HOST` in `application-delivery.yml`; document override in `env.shared.template` |
| 7 | ID strategy mismatch with `orders-service` | Low | Pick BIGINT to match existing schema; document in ADR |
| 8 | 400-line PR budget exceeded | Med | Chained PRs recommended: PR1 = InternalApiKeyFilter + shared-observability; PR2 = JPA + Flyway + adapter swap; PR3 = test replacement + boot test |

## 12. Out-of-scope follow-ups

- FR-3 (`deliveryPersonId` filter) — still WARNING from prior archive; needs DB column + design decision about which service owns it.
- `juniors/` cleanup — Task-5 from prior archive lives in working tree only; commit is separate work.
- HTTP-based `OrderServiceClientAdapter` (decision §8.1 option B) — gated on orders-service exposing endpoints.
- Moving `catalog-service` and `auth-service` to dedicated DBs (catalog already runs Flyway disabled).
- `services/orders-service/` legacy JDBC code (not the migrated `juniors/junior-2-orders/`).

---

## Capabilities (contract with sdd-spec)

### New Capabilities
- `service-internal-auth`: shared `X-Internal-Api-Key` filter contract across services.
- `delivery-persistence`: dedicated Postgres adapter for delivery-service via Spring Data JPA + Flyway.

### Modified Capabilities
- None at the spec level. The public REST contract of `delivery-service` is unchanged (same paths, same `ApiResponse`, same `code` field). Pure implementation migration.

## Approach (one-paragraph summary)

Wire `delivery-service` to the already-provisioned `delivery_db` by adding Spring Data JPA + Flyway dependencies, introducing a `delivery` Spring profile with its own `DataSource`, owning two Flyway migrations for `internal.delivery` and `internal.delivery_routes`, and replacing the two Supabase REST outbound adapters with JPA-backed implementations of the same ports. Concurrently, create an `InternalApiKeyFilter` in `shared-observability` and auto-register it so every service authenticates internal traffic. The `OrderServiceClientAdapter` decision (§8.1) is deferred to design. Tests stay green by virtue of port-level mocking; the single Supabase-direct test is replaced by a Testcontainers-backed SQL test.

## Affected areas

| Area | Impact | Description |
|------|--------|-------------|
| `services/delivery-service/build.gradle.kts` | Modified | Add `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql` |
| `services/delivery-service/src/main/resources/application-delivery.yml` | New | Dedicated `DataSource` for `delivery_db` |
| `services/delivery-service/src/main/resources/db/migration/` | New | Two Flyway scripts |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/jpa/` | New | JPA entities + Spring Data repos |
| `services/delivery-service/src/main/java/.../infrastructure/adapter/outbound/persistence/supabase/` | Removed | Supabase adapters retire |
| `services/shared-observability/src/main/java/com/flashdrop/observability/security/InternalApiKeyFilter.java` | New | Shared filter |
| `services/shared-observability/src/main/java/com/flashdrop/observability/config/ObservabilityAutoConfiguration.java` | Modified | Register filter bean |
| `infra/coolify/env.shared.template` | Modified | Add `DELIVERY_DB_HOST`, `DELIVERY_SPRING_PROFILES_ACTIVE` |
| `services/delivery-service/src/test/.../SupabaseRestRouteRepositoryTest.java` | Removed | Replaced by JPA integration test |
| `services/delivery-service/src/test/.../JpaRouteRepositoryIT.java` | New | Testcontainers-backed |

## Dependencies

- `delivery_db` + `delivery_svc` user (already provisioned in `infra/coolify/01-postgres-init.sql`).
- Testcontainers + PostgreSQL on the test classpath (already used by juniors).
- Java 17 toolchain (env constraint from prior ADR-005).

## Rollback plan

Per-PR rollback (chained delivery):
1. Revert the JPA adapter PR → service falls back to reading via the previous Supabase adapters (kept in git history until PR merge).
2. Revert the `InternalApiKeyFilter` PR → other services lose the filter; gateway continues routing; auth boundary reverts to "any caller can hit any internal route" (the pre-change state).
3. The Flyway migration is reversible via a paired `U__*.sql` script in the same PR; or `flywayClean` in non-prod.

The change is **additive on the data plane** (new DB, no shared-table mutations) and **substitutive on the adapter plane** (port interface unchanged), so any individual PR can be reverted without breaking other services.

## Success criteria

- [ ] `./gradlew :delivery-service:bootJar` succeeds with the new `delivery` profile active.
- [ ] Container boots against `delivery_db`, Flyway runs both migrations, `/actuator/health` returns UP.
- [ ] All 19 existing unit tests still pass without modification (port-level mocks).
- [ ] New Testcontainers integration test (`JpaRouteRepositoryIT`) passes.
- [ ] `POST /api/delivery/claim` and `GET /api/delivery/routes` round-trip a `delivery_routes` row through JPA and return the same shape as the previous Supabase-backed responses.
- [ ] Request without `X-Internal-Api-Key` returns 401 with `ApiError` body.
- [ ] Gateway continues to route `/api/delivery` → `delivery-service:8084` with zero gateway-side changes.
- [ ] No `jakarta.persistence` import exists under `domain/` or `application/` (architecture test enforced).
