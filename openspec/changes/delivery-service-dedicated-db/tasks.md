# Tasks: `delivery-service-dedicated-db`

## PR Chain

**Strategy**: `stacked-to-main` (mirrors `consolidate-delivery-into-delivery-service/archive-report` branch chain)
**Chain strategy cache**: `stacked-to-main` (prior archive precedent; no explicit override requested)

```
main
  └── feat/shared-observability-internal-api-key            (PR-1)
        └── feat/delivery-jpa-adapter-swap                   (PR-2)
              └── feat/delivery-internal-controllers          (PR-3)
                    └── chore/delivery-testcontainers-it-and-cleanup  (PR-4)
```

---

## Task List

### PR-1 — `feat/shared-observability-internal-api-key`

**Branch**: `feat/shared-observability-internal-api-key` (base: `main`)
**Scope**: InternalApiKeyFilter + shared-observability wiring + env template doc
**Est. LOC**: ~280 | **Files**: ~7

---

#### T-1 — InternalApiKeyFilter core implementation
- **Jira**: KAN-36 (Security: internal API key filter for service-to-service auth)
- **Files**: `services/shared-observability/src/main/java/com/flashdrop/observability/security/InternalApiKeyFilter.java`
- **TDD RED first**: N/A (greenfield filter; write unit test `InternalApiKeyFilterTest.java` first)
- **Acceptance**: Filter validates `X-Internal-Api-Key` via `MessageDigest.isEqual`; missing/wrong key returns HTTP 401 with `ApiError(code="UNAUTHORIZED", message="Missing or invalid internal API key")`; header absent + `dev` profile = filter disabled
- **Commit**: `feat(shared-observability): add InternalApiKeyFilter with constant-time compare`

---

#### T-2 — InternalApiKeyFilter auto-registration
- **Jira**: KAN-36 (same ticket — same logical unit of work)
- **Files**: `services/shared-observability/src/main/java/com/flashdrop/observability/config/ObservabilityAutoConfiguration.java`
- **TDD RED first**: `InternalApiKeyFilterTest.java` (already RED before wiring)
- **Acceptance**: `ObservabilityAutoConfiguration` registers `InternalApiKeyFilter` as a `FilterRegistrationBean`; filter activates when `INTERNAL_API_KEY` env is present and profile is not `dev`
- **Commit**: `feat(shared-observability): register InternalApiKeyFilter via ObservabilityAutoConfiguration`

---

#### T-3 — Filter unit tests
- **Jira**: KAN-36
- **Files**: `services/shared-observability/src/test/java/com/flashdrop/observability/security/InternalApiKeyFilterTest.java`
- **TDD RED first**: Yes — test file exists before production filter is wired end-to-end
- **Acceptance**: `MockMvc` asserts: correct key → 200, missing key → 401 body shape, wrong key → 401 body shape, `dev` profile → 200 even without key
- **Commit**: `test(shared-observability): add InternalApiKeyFilter unit tests with MockMvc`

---

#### T-4 — shared-observability build.gradle.kts update
- **Jira**: no ticket — internal dependency update
- **Files**: `services/shared-observability/build.gradle.kts`
- **TDD RED first**: N/A (build file only)
- **Acceptance**: `spring-boot-starter-web` present (required for `OncePerRequestFilter`); project compiles
- **Commit**: `chore(shared-observability): add spring-boot-starter-web for filter base class`

---

#### T-5 — env.shared.template DELIVERY_DB_HOST/PORT documentation
- **Jira**: KAN-47 (Docs: update README and gateway CLAUDE.md)
- **Files**: `infra/coolify/env.shared.template`
- **TDD RED first**: N/A (config file)
- **Acceptance**: `DELIVERY_DB_HOST=${POSTGRES_HOST}`, `DELIVERY_DB_PORT=${POSTGRES_PORT}`, `DELIVERY_SPRING_PROFILES_ACTIVE=delivery` documented; existing `DELIVERY_DB_NAME=delivery_db` and `DELIVERY_DB_USER` unchanged
- **Commit**: `docs(infra): document DELIVERY_DB_HOST/PORT/SPRING_PROFILES_ACTIVE in env.shared.template`

---

### PR-2 — `feat/delivery-jpa-adapter-swap`

**Branch**: `feat/delivery-jpa-adapter-swap` (base: `main` — stacked-to-main)
**Scope**: JPA + Flyway + adapter swap + DEC-2 select fix
**Est. LOC**: ~395 | **Files**: ~18

---

#### T-6 — delivery-service build.gradle.kts: add JPA/Flyway/Postgres deps
- **Jira**: KAN-37 (Infra: wiring the delivery-service to delivery_db)
- **Files**: `services/delivery-service/build.gradle.kts`
- **TDD RED first**: N/A (build file)
- **Acceptance**: `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`, `org.postgresql:postgresql` (runtime), `org.testcontainers:postgresql` + `junit-jupiter` (test) all present; Java 21 source/target preserved per ADR-5
- **Commit**: `feat(delivery): add spring-boot-starter-data-jpa flyway postgres testcontainers`

---

#### T-7 — application-delivery.yml: dedicated DataSource
- **Jira**: KAN-37 (same ticket)
- **Files**: `services/delivery-service/src/main/resources/application-delivery.yml`
- **TDD RED first**: N/A (config file)
- **Acceptance**: `spring.datasource.url=jdbc:postgresql://${DELIVERY_DB_HOST:POSTGRES_HOST}:${DELIVERY_DB_PORT:5432}/${DELIVERY_DB_NAME:delivery_db}`, user/password from env; `jpa.hibernate.ddl-auto=validate`; `flyway.enabled=true`; `flyway.schemas=internal`; `spring.jpa.properties.hibernate.default_schema=internal`
- **Commit**: `feat(delivery): add delivery Spring profile with dedicated DataSource`

---

#### T-8 — V1__create-tables.sql
- **Jira**: KAN-37
- **Files**: `services/delivery-service/src/main/resources/db/migration/V1__create-tables.sql`
- **TDD RED first**: N/A (DDL — no TDD applicable)
- **Acceptance**: Creates `internal.delivery` (id BIGSERIAL PK, code UNIQUE, address, status, created_at + idx), `internal.delivery_routes` (id BIGSERIAL PK, FK→delivery, order_id, nullable delivery_person_id, status, timestamps + idx), `internal.delivery_persons` (id BIGSERIAL PK, user_id UNIQUE, active, created_at). Plain `CREATE TABLE` (no `IF NOT EXISTS`). Covers FR-1, FR-7.
- **Commit**: `feat(delivery): add V1__create-tables.sql Flyway migration for internal schema`

---

#### T-9 — V2__seed-delivery_persons.sql
- **Jira**: KAN-38 (Seed: V2 seed script for delivery_persons)
- **Files**: `services/delivery-service/src/main/resources/db/migration/V2__seed-delivery_persons.sql`
- **TDD RED first**: N/A (seed DDL)
- **Acceptance**: Idempotent `INSERT … ON CONFLICT (user_id) DO NOTHING` seeds one `delivery_persons` row (user_id='U1', active=true). FR-5 + Scenario 1 satisfied.
- **Commit**: `feat(delivery): add V2__seed-delivery_persons.sql idempotent seed`

---

#### T-10 — JPA entity: DeliveryRouteJpaEntity
- **Jira**: KAN-37
- **Files**: `services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/outbound/persistence/jpa/DeliveryRouteJpaEntity.java`
- **TDD RED first**: `JpaRouteRepositoryIT.java` RED before entity exists
- **Acceptance**: JPA entity for `internal.delivery_routes`; `BIGINT` id with `@GeneratedValue(strategy=IDENTITY)` per ADR-3; all columns mapped; relationships correct; no Spring imports outside `infrastructure/` package
- **Commit**: `feat(delivery): add DeliveryRouteJpaEntity for internal.delivery_routes`

---

#### T-11 — JPA entity: DeliveryPersonJpaEntity
- **Jira**: KAN-37
- **Files**: `services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/outbound/persistence/jpa/DeliveryPersonJpaEntity.java`
- **TDD RED first**: `JpaDeliveryPersonRepositoryIT.java` RED before entity exists
- **Acceptance**: JPA entity for `internal.delivery_persons`; `BIGINT IDENTITY` id; `userId VARCHAR(64) UNIQUE`; `active BOOLEAN`; zero framework imports in domain/application
- **Commit**: `feat(delivery): add DeliveryPersonJpaEntity for internal.delivery_persons`

---

#### T-12 — JpaRouteRepository (Spring Data JPA)
- **Jira**: KAN-37
- **Files**: `services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/outbound/persistence/jpa/JpaRouteRepository.java`
- **TDD RED first**: `JpaRouteRepositoryIT.java` RED before repo exists
- **Acceptance**: Extends `JpaRepository<DeliveryRouteJpaEntity, Long>`; implements `RouteRepositoryPort`; CRUD ops for `RouteRepositoryPort` methods; lives under `infrastructure/adapter/outbound/persistence/jpa/`
- **Commit**: `feat(delivery): add JpaRouteRepository implementing RouteRepositoryPort`

---

#### T-13 — JpaDeliveryPersonRepository (Spring Data JPA)
- **Jira**: KAN-37
- **Files**: `services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/outbound/persistence/jpa/JpaDeliveryPersonRepository.java`
- **TDD RED first**: `JpaDeliveryPersonRepositoryIT.java` RED before repo exists
- **Acceptance**: Extends `JpaRepository<DeliveryPersonJpaEntity, Long>`; implements `DeliveryPersonRepositoryPort`; `findByUserId(String userId)` method present
- **Commit**: `feat(delivery): add JpaDeliveryPersonRepository implementing DeliveryPersonRepositoryPort`

---

#### T-14 — PersistenceConfig
- **Jira**: KAN-37
- **Files**: `services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/outbound/persistence/jpa/PersistenceConfig.java`
- **TDD RED first**: N/A (config class)
- **Acceptance**: `@EnableJpaRepositories(basePackages=…)`, `@EntityScan(basePackages=…)`, `@Profile("delivery")` guard; JPA metadata and repo scanning active only under `delivery` profile
- **Commit**: `feat(delivery): add PersistenceConfig for delivery profile JPA setup`

---

#### T-15 — Delete Supabase REST adapters
- **Jira**: KAN-37
- **Files**: `SupabaseRestRouteRepository.java`, `SupabaseRestDeliveryPersonRepository.java`, `DeliveryRouteRow.java`, `DeliveryRow.java` (under `infrastructure/adapter/outbound/persistence/supabase/`)
- **TDD RED first**: N/A (deletion)
- **Acceptance**: All four files removed; `RouteRepositoryPort` and `DeliveryPersonRepositoryPort` still satisfied by new JPA repos; use-case unit tests still pass via port mocks; `RestaurantRow.java` kept (still used by `OrderServiceClientAdapter`)
- **Commit**: `refactor(delivery): remove Supabase REST adapters replaced by JPA`

---

#### T-16 — DEC-2 fix: OrderRow.clientId drop + OrderServiceClientAdapter select fix
- **Jira**: KAN-39 (Fix: OrderRow.clientId and PostgREST select bug)
- **Files**: `OrderRow.java`, `OrderServiceClientAdapter.java` (under `infrastructure/adapter/outbound/persistence/supabase/`)
- **TDD RED first**: `OrderServiceClientAdapterTest.java` RED before select fix
- **Acceptance**: `clientId` field removed from `OrderRow`; `client_id` removed from PostgREST `select=...` string in `OrderServiceClientAdapter`; adapter compiles and existing test passes
- **Commit**: `fix(delivery): remove clientId from OrderRow and drop from PostgREST select`

---

### PR-3 — `feat/delivery-internal-controllers`

**Branch**: `feat/delivery-internal-controllers` (base: `main` — stacked-to-main)
**Scope**: Internal REST controllers + InternalApiKeyFilterRegistration for delivery-service
**Est. LOC**: ~180 | **Files**: ~5

---

#### T-17 — InternalDeliveryPersonsController
- **Jira**: KAN-40 (Internal: GET /api/internal/delivery-persons)
- **Files**: `services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/inbound/rest/InternalDeliveryPersonsController.java`
- **TDD RED first**: `InternalDeliveryPersonsControllerTest.java` RED before controller
- **Acceptance**: `@RequestMapping("/api/internal/delivery-persons")`; `GET ?userId=…` backed by `DeliveryPersonRepositoryPort.findByUserId(...)`; returns `ApiResponse<DeliveryPerson>`; protected by `InternalApiKeyFilter` (Scenarios 4/5/6)
- **Commit**: `feat(delivery): add InternalDeliveryPersonsController for internal API`

---

#### T-18 — InternalRoutesController
- **Jira**: KAN-41 (Internal: POST /api/internal/routes)
- **Jira**: KAN-42 (Internal: PATCH /api/internal/routes/{orderId}/status)
- **Files**: `services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/inbound/rest/InternalRoutesController.java`
- **TDD RED first**: `InternalRoutesControllerTest.java` RED before controller
- **Acceptance**: `@RequestMapping("/api/internal/routes")`; `POST /` with `CreateDeliveryRouteRequest` body → 201 + `ApiResponse<Route>`; `PATCH /{orderId}/status` → 200; both protected by `InternalApiKeyFilter` (Scenarios 7/8/9)
- **Commit**: `feat(delivery): add InternalRoutesController for internal route management`

---

#### T-19 — InternalApiKeyFilterRegistration (delivery-service explicit bean)
- **Jira**: KAN-43 (Internal: filter registration for delivery-service)
- **Files**: `services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/config/InternalApiKeyFilterRegistration.java`
- **TDD RED first**: N/A (config bean)
- **Acceptance**: `@Profile("!dev")` bean registering `InternalApiKeyFilter` explicitly in delivery-service; delivery-service picks up filter even if auto-config misses it
- **Commit**: `feat(delivery): register InternalApiKeyFilter bean for delivery-service`

---

#### T-20 — InternalDeliveryPersonsControllerTest
- **Jira**: KAN-40 (same ticket as T-17)
- **Files**: `services/delivery-service/src/test/java/com/flashdrop/delivery/infrastructure/adapter/inbound/rest/InternalDeliveryPersonsControllerTest.java`
- **TDD RED first**: Yes — test written before controller
- **Acceptance**: `@WebMvcTest` covering: no key → 401 (Scenario 4), wrong key → 401 (Scenario 5), correct key + seeded user → 200 with `ApiResponse<DeliveryPerson>` (Scenario 6)
- **Commit**: `test(delivery): add InternalDeliveryPersonsController WebMvc tests`

---

#### T-21 — InternalRoutesControllerTest
- **Jira**: KAN-41, KAN-42
- **Files**: `services/delivery-service/src/test/java/com/flashdrop/delivery/infrastructure/adapter/inbound/rest/InternalRoutesControllerTest.java`
- **TDD RED first**: Yes — test written before controller
- **Acceptance**: `@WebMvcTest` covering: POST valid → 201 (Scenario 7), POST invalid → 400 (Scenario 8), PATCH status → 200 (Scenario 9); all with correct key; missing/wrong key → 401
- **Commit**: `test(delivery): add InternalRoutesController WebMvc tests`

---

### PR-4 — `chore/delivery-testcontainers-it-and-cleanup`

**Branch**: `chore/delivery-testcontainers-it-and-cleanup` (base: `main` — stacked-to-main)
**Scope**: Testcontainers IT + architecture test + docs + cleanup
**Est. LOC**: ~280 | **Files**: ~10

---

#### T-22 — JpaRouteRepositoryIT (Testcontainers)
- **Jira**: KAN-44 (Test: JPA repository integration tests)
- **Files**: `services/delivery-service/src/test/java/com/flashdrop/delivery/infrastructure/adapter/outbound/persistence/jpa/JpaRouteRepositoryIT.java`
- **TDD RED first**: Yes — test written before JPA repo production code (already covered by T-12, but IT is PR-4 scope)
- **Acceptance**: `@Testcontainers`, JUnit 5, Postgres 16 container; Flyway runs V1+V2 on container init; CRUD round-trip on `JpaRouteRepository`; no port mocks; covers FR-2
- **Test runner**: `./gradlew :delivery-service:test --tests 'JpaRouteRepositoryIT'`
- **Commit**: `test(delivery): add JpaRouteRepositoryIT with Testcontainers Postgres`

---

#### T-23 — JpaDeliveryPersonRepositoryIT (Testcontainers)
- **Jira**: KAN-44 (same ticket)
- **Files**: `services/delivery-service/src/test/java/com/flashdrop/delivery/infrastructure/adapter/outbound/persistence/jpa/JpaDeliveryPersonRepositoryIT.java`
- **TDD RED first**: Yes — test written before JPA repo production code (already covered by T-13, but IT is PR-4 scope)
- **Acceptance**: `@Testcontainers`; `findByUserId` happy-path; seeded `U1` row present after Flyway; no mocks
- **Test runner**: `./gradlew :delivery-service:test --tests 'JpaDeliveryPersonRepositoryIT'`
- **Commit**: `test(delivery): add JpaDeliveryPersonRepositoryIT with Testcontainers Postgres`

---

#### T-24 — DeliveryServiceApplicationIT (boot + Flyway)
- **Jira**: KAN-45 (Test: architecture test for hexagonal purity)
- **Files**: `services/delivery-service/src/test/java/com/flashdrop/delivery/DeliveryServiceApplicationIT.java`
- **TDD RED first**: Yes — boot test written before full production wiring
- **Acceptance**: `@SpringBootTest` against Testcontainer `delivery_db`; asserts Flyway runs both V1 + V2; `/actuator/health` returns UP; covers FR-1 + Scenario 1 + Scenario 2 (failure path verified by checking Flyway error propagation)
- **Test runner**: `./gradlew :delivery-service:test --tests 'DeliveryServiceApplicationIT'`
- **Commit**: `test(delivery): add DeliveryServiceApplicationIT boot integration test`

---

#### T-25 — DeliveryMigrationIT (BDD seed scenario)
- **Jira**: KAN-45 (same ticket)
- **Files**: `services/delivery-service/src/test/java/com/flashdrop/delivery/DeliveryMigrationIT.java`
- **TDD RED first**: Yes — migration scenario test written before migration scripts
- **Acceptance**: Given empty DB, when app boots, then all three tables exist AND seeded `delivery_persons` row for `U1` is present (Scenarios 1, 10). Idempotency: second Flyway run completes without error and inserts no duplicates.
- **Test runner**: `./gradlew :delivery-service:test --tests 'DeliveryMigrationIT'`
- **Commit**: `test(delivery): add DeliveryMigrationIT for BDD seed scenario`

---

#### T-26 — DeliveryArchitectureTest (hexagonal purity enforcement)
- **Jira**: KAN-45 (same ticket)
- **Files**: `services/delivery-service/src/test/java/com/flashdrop/delivery/DeliveryArchitectureTest.java`
- **TDD RED first**: N/A (architecture test — rules written before enforcement code exists)
- **Acceptance**: ArchUnit test (or equivalent) forbids `jakarta.persistence`, `org.springframework.data`, `org.springframework.boot` in `domain/` and `application/` packages; runs in CI; covers NFR-1
- **Commit**: `test(delivery): add DeliveryArchitectureTest for hexagonal layer purity`

---

#### T-27 — README.md and gateway/CLAUDE.md docs update
- **Jira**: KAN-46 (Docs: update README and gateway CLAUDE.md)
- **Files**: `README.md`, `gateway/CLAUDE.md`
- **TDD RED first**: N/A (docs)
- **Acceptance**: README: one sentence added noting `delivery-service` now owns `delivery_db` via Spring Data JPA + Flyway. gateway/CLAUDE.md: note that `/api/delivery` is SQL-backed; `delivery_db` env host/PORT required
- **Commit**: `docs: note delivery_db ownership and env requirements`

---

#### T-28 — Delete SupabaseRestRouteRepositoryTest
- **Jira**: KAN-47 (Cleanup: remove obsolete Supabase test)
- **Files**: `services/delivery-service/src/test/java/com/flashdrop/delivery/infrastructure/adapter/outbound/persistence/supabase/SupabaseRestRouteRepositoryTest.java`
- **TDD RED first**: N/A (deletion)
- **Acceptance**: File removed; replaced by `JpaRouteRepositoryIT` in same PR; no orphaned test references
- **Commit**: `test(delivery): remove SupabaseRestRouteRepositoryTest replaced by JPA IT`

---

#### T-29 — Existing unit tests still green
- **Jira**: KAN-48 (no ticket — CI gate; internal verification)
- **Files**: All existing test files under `services/delivery-service/src/test/java/`
- **TDD RED first**: N/A (existing tests — verify they remain green)
- **Acceptance**: All 19 existing unit tests pass without modification; `./gradlew :delivery-service:test --tests '*UseCaseImplTest' --tests '*ControllerTest'` all green; port-level mocks unchanged
- **Commit**: `chore(delivery): verify all existing unit tests still pass`

---

## PR Boundaries

### PR-1: `feat/shared-observability-internal-api-key`
| Field | Value |
|---|---|
| **Branch** | `feat/shared-observability-internal-api-key` (base: `main`) |
| **Title** | `feat(shared-observability): add InternalApiKeyFilter for service-to-service auth` |
| **Files** | `InternalApiKeyFilter.java`, `ObservabilityAutoConfiguration.java`, `InternalApiKeyFilterTest.java`, `shared-observability/build.gradle.kts`, `infra/coolify/env.shared.template` |
| **Est. LOC** | ~280 |
| **Verification** | `cd services/shared-observability && ./gradlew test` |
| **Rollback** | `git revert HEAD` |

### PR-2: `feat/delivery-jpa-adapter-swap`
| Field | Value |
|---|---|
| **Branch** | `feat/delivery-jpa-adapter-swap` (base: `main`) |
| **Title** | `feat(delivery): wire delivery-service to dedicated delivery_db via JPA + Flyway` |
| **Files** | `build.gradle.kts`, `application-delivery.yml`, `V1__create-tables.sql`, `V2__seed-delivery_persons.sql`, `DeliveryRouteJpaEntity.java`, `DeliveryPersonJpaEntity.java`, `JpaRouteRepository.java`, `JpaDeliveryPersonRepository.java`, `PersistenceConfig.java`, `OrderRow.java` (modify), `OrderServiceClientAdapter.java` (modify), `SupabaseRestRouteRepository.java` (delete), `SupabaseRestDeliveryPersonRepository.java` (delete), `DeliveryRouteRow.java` (delete), `DeliveryRow.java` (delete) |
| **Est. LOC** | ~395 |
| **Verification** | `./gradlew :delivery-service:test` + `./gradlew :delivery-service:bootJar -Pspring.profiles.active=delivery` |
| **Rollback** | `git revert HEAD` + non-prod: `./gradlew :delivery-service:flywayClean -Pflyway.schemas=internal` |

### PR-3: `feat/delivery-internal-controllers`
| Field | Value |
|---|---|
| **Branch** | `feat/delivery-internal-controllers` (base: `main`) |
| **Title** | `feat(delivery): add /api/internal/* controllers for delivery persons and routes` |
| **Files** | `InternalDeliveryPersonsController.java`, `InternalRoutesController.java`, `InternalApiKeyFilterRegistration.java`, `InternalDeliveryPersonsControllerTest.java`, `InternalRoutesControllerTest.java` |
| **Est. LOC** | ~180 |
| **Verification** | `./gradlew :delivery-service:test --tests '*ControllerTest'` |
| **Rollback** | `git revert HEAD` |

### PR-4: `chore/delivery-testcontainers-it-and-cleanup`
| Field | Value |
|---|---|
| **Branch** | `chore/delivery-testcontainers-it-and-cleanup` (base: `main`) |
| **Title** | `test(delivery): add Testcontainers IT, architecture test, and docs` |
| **Files** | `JpaRouteRepositoryIT.java`, `JpaDeliveryPersonRepositoryIT.java`, `DeliveryServiceApplicationIT.java`, `DeliveryMigrationIT.java`, `DeliveryArchitectureTest.java`, `README.md` (modify), `gateway/CLAUDE.md` (modify), `SupabaseRestRouteRepositoryTest.java` (delete) |
| **Est. LOC** | ~280 |
| **Verification** | `./gradlew :delivery-service:test --tests '*IT'` + `./gradlew :delivery-service:test --tests '*ArchitectureTest'` |
| **Rollback** | `git revert HEAD` |

---

## Review Workload Forecast

| Metric | Value |
|---|---|
| **Total changed lines** | ~1,135 |
| **PR-1 LOC** | ~280 |
| **PR-2 LOC** | ~395 |
| **PR-3 LOC** | ~180 |
| **PR-4 LOC** | ~280 |
| **Per-PR file count** | PR-1: ~7, PR-2: ~18, PR-3: ~5, PR-4: ~10 |
| **Est. review minutes per PR** | PR-1: ~14 min, PR-2: ~20 min, PR-3: ~9 min, PR-4: ~14 min |
| **Chained PRs recommended** | **Yes** — 4 PRs, each <= 400 lines |
| **400-line budget risk** | **Low** — max per-PR is 395 (PR-2), below 400 threshold |
| **Decision needed before apply** | **No** — delivery_strategy=`auto-chain` cached; chain_strategy=`stacked-to-main` assumed from prior archive; PR chain is within budget |

---

## Jira Ticket Sync Plan

| Ticket | Summary | Closing Task | Transition Sequence | Transition Comment |
|---|---|---|---|---|
| **KAN-21** | Migración 2027: Delivery (parent) | T-29 (all PRs collectively close children) | Por hacer → En curso → En revisión → Finalizado | "All 4 chained PRs merged. delivery-service now owns delivery_db. InternalApiKeyFilter active across all services." |
| **KAN-36** | Security: internal API key filter | T-1, T-2, T-3 | Por hacer → En curso → En revisión → Finalizado | "PR-1 merged: InternalApiKeyFilter in shared-observability; auto-registered via ObservabilityAutoConfiguration. Tests: InternalApiKeyFilterTest passes." |
| **KAN-37** | Infra: wiring delivery-service to delivery_db | T-6, T-7, T-8, T-10, T-11, T-12, T-13, T-14, T-15 | Por hacer → En curso → En revisión → Finalizado | "PR-2 merged: JPA + Flyway wired; delivery_db owned by delivery_svc; Supabase adapters removed. bootJar succeeds with delivery profile." |
| **KAN-38** | Seed: V2 seed script for delivery_persons | T-9 | Por hacer → En curso → En revisión → Finalizado | "PR-2 merged: V2__seed-delivery_persons.sql idempotently seeds U1. DeliveryMigrationIT confirms seed." |
| **KAN-39** | Fix: OrderRow.clientId and PostgREST select bug | T-16 | Por hacer → En curso → En revisión → Finalizado | "PR-2 merged: client_id dropped from PostgREST select; OrderRow.clientId removed. OrderServiceClientAdapterTest passes." |
| **KAN-40** | Internal: GET /api/internal/delivery-persons | T-17, T-20 | Por hacer → En curso → En revisión → Finalizado | "PR-3 merged: InternalDeliveryPersonsController live at /api/internal/delivery-persons; Scenarios 4/5/6 verified by InternalDeliveryPersonsControllerTest." |
| **KAN-41** | Internal: POST /api/internal/routes | T-18, T-21 | Por hacer → En curso → En revisión → Finalizado | "PR-3 merged: InternalRoutesController POST /api/internal/routes live; Scenario 7 verified." |
| **KAN-42** | Internal: PATCH /api/internal/routes/{orderId}/status | T-18, T-21 | Por hacer → En curso → En revisión → Finalizado | "PR-3 merged: InternalRoutesController PATCH status endpoint live; Scenario 9 verified." |
| **KAN-43** | Internal: filter registration for delivery-service | T-19 | Por hacer → En curso → En revisión → Finalizado | "PR-3 merged: InternalApiKeyFilterRegistration bean registered in delivery-service under !dev profile." |
| **KAN-44** | Test: JPA repository integration tests | T-22, T-23 | Por hacer → En curso → En revisión → Finalizado | "PR-4 merged: JpaRouteRepositoryIT + JpaDeliveryPersonRepositoryIT pass with Testcontainers Postgres 16." |
| **KAN-45** | Test: architecture test for hexagonal purity | T-24, T-25, T-26 | Por hacer → En curso → En revisión → Finalizado | "PR-4 merged: DeliveryArchitectureTest forbids jakarta.persistence in domain/application; DeliveryServiceApplicationIT confirms boot + Flyway; DeliveryMigrationIT confirms seed idempotency." |
| **KAN-46** | Docs: update README and gateway CLAUDE.md | T-27 | Por hacer → En curso → En revisión → Finalizado | "PR-4 merged: README notes delivery_db ownership; gateway/CLAUDE.md documents env requirements." |
| **KAN-47** | Cleanup: remove obsolete Supabase test | T-28 | Por hacer → En curso → En revisión → Finalizado | "PR-4 merged: SupabaseRestRouteRepositoryTest deleted, replaced by JpaRouteRepositoryIT." |

---

## Open Follow-ups

### DEC-1: Refactor OrderServiceClientAdapter to HTTP
**Description**: Move `OrderServiceClientAdapter` from Supabase REST reads of shared `orders`/`restaurant` tables to HTTP calls against `orders-service` endpoints (true service isolation).
**Gated on**: `orders-service` exposing HTTP endpoints per `OrderServicePort` methods.
**Follow-up ticket**: Create new Jira ticket (e.g., KAN-50) in the KAN-21 epic to track this.
**ADR**: ADR-1 from design documents this as accepted tech debt.

### juniors/ legacy cleanup
**Description**: `juniors/junior-2-orders/` still contains `DeliveryRouteController.java`, `DeliveryRouteResponse.java`, and a test file that were marked "working tree only" in the prior archive (ID: `sdd/consolidate-delivery-into-delivery-service/archive-report`). These have not been committed.
**Follow-up ticket**: Create new Jira ticket (e.g., KAN-51) for `juniors/` cleanup — separate from this change's scope.
**Note**: This is explicitly out of scope per proposal section 7 and design section 12.

### env.shared.template MINOR gap (m2 from verify-report)
**Description**: `env.shared.template` still lacks `DELIVERY_DB_HOST` and `DELIVERY_DB_PORT` entries. `application-delivery.yml` defaults to `${POSTGRES_HOST}` / `${POSTGRES_PORT}` so this is not a deploy blocker, but the template should be closed before production deploy.
**Resolution**: Already fixed in PR-1 (T-5). No separate follow-up ticket needed.

---

## Strict TDD Application Per Task

| Task | RED test file | Production code | GREEN | Refactor |
|---|---|---|---|---|
| T-1 | `InternalApiKeyFilterTest.java` | `InternalApiKeyFilter.java` | Test passes correct/wrong/missing key scenarios | Extract `ApiError` constant |
| T-2 | `InternalApiKeyFilterTest.java` (same file — covers wiring) | `ObservabilityAutoConfiguration.java` | Filter auto-registered when env present | N/A |
| T-3 | `InternalApiKeyFilterTest.java` | `InternalApiKeyFilter.java` (full) | All MockMvc assertions pass | N/A |
| T-6 | N/A (build file) | `build.gradle.kts` deps added | `./gradlew :delivery-service:compileJava` | N/A |
| T-7 | N/A (config file) | `application-delivery.yml` | `./gradlew :delivery-service:bootJar -Pspring.profiles.active=delivery` | N/A |
| T-8 | N/A (DDL) | `V1__create-tables.sql` | `DeliveryServiceApplicationIT` confirms tables created | N/A |
| T-9 | N/A (DDL seed) | `V2__seed-delivery_persons.sql` | `DeliveryMigrationIT` confirms U1 seeded | N/A |
| T-10 | `JpaRouteRepositoryIT.java` | `DeliveryRouteJpaEntity.java` | IT CRUD round-trip passes | N/A |
| T-11 | `JpaDeliveryPersonRepositoryIT.java` | `DeliveryPersonJpaEntity.java` | IT findByUserId passes | N/A |
| T-12 | `JpaRouteRepositoryIT.java` (same — repo uses entity) | `JpaRouteRepository.java` | IT passes with real Hibernate | N/A |
| T-13 | `JpaDeliveryPersonRepositoryIT.java` (same) | `JpaDeliveryPersonRepository.java` | IT passes | N/A |
| T-14 | N/A (config) | `PersistenceConfig.java` | Boot test confirms JPA context loaded | N/A |
| T-15 | N/A (deletion) | Adapters removed | Use case tests still green via mocks | N/A |
| T-16 | `OrderServiceClientAdapterTest.java` | `OrderRow.java`, `OrderServiceClientAdapter.java` | Test passes; no `client_id` in select | N/A |
| T-17 | `InternalDeliveryPersonsControllerTest.java` | `InternalDeliveryPersonsController.java` | WebMvc test Scenarios 4/5/6 pass | N/A |
| T-18 | `InternalRoutesControllerTest.java` | `InternalRoutesController.java` | WebMvc test Scenarios 7/8/9 pass | N/A |
| T-19 | N/A (config bean) | `InternalApiKeyFilterRegistration.java` | Boot + `dev` profile = filter absent; non-dev = filter present | N/A |
| T-20 | `InternalDeliveryPersonsControllerTest.java` | `InternalDeliveryPersonsController.java` | Already GREEN from T-17 | N/A |
| T-21 | `InternalRoutesControllerTest.java` | `InternalRoutesController.java` | Already GREEN from T-18 | N/A |
| T-22 | `JpaRouteRepositoryIT.java` | `JpaRouteRepository.java` + entities | IT passes; Flyway V1+V2 run on container | N/A |
| T-23 | `JpaDeliveryPersonRepositoryIT.java` | `JpaDeliveryPersonRepository.java` + entity | IT passes | N/A |
| T-24 | `DeliveryServiceApplicationIT.java` | All PR-2 production code | Boot + health UP | N/A |
| T-25 | `DeliveryMigrationIT.java` | `V2__seed-delivery_persons.sql` | Seed row present; idempotency confirmed | N/A |
| T-26 | `DeliveryArchitectureTest.java` | ArchUnit rules defined | No `jakarta.persistence` in domain/application | N/A |
| T-27 | N/A (docs) | `README.md`, `gateway/CLAUDE.md` | N/A | N/A |
| T-28 | N/A (deletion) | `SupabaseRestRouteRepositoryTest.java` removed | `JpaRouteRepositoryIT` provides coverage | N/A |
| T-29 | N/A (existing tests) | N/A | `./gradlew :delivery-service:test --tests '*UseCaseImplTest' --tests '*ControllerTest'` all green | N/A |

**Test runner command** (delivery-service): `./gradlew :delivery-service:test`
**Test runner command** (shared-observability): `./gradlew :shared-observability:test`

---

## Dependency Graph

```
T-1 (filter) ──────────────┐
T-2 (auto-config) ────────┤
T-3 (unit tests) ─────────┤
T-4 (build.gradle) ────────┤
T-5 (env.template) ────────┼──► PR-1: feat/shared-observability-internal-api-key

T-6 (deps) ────────────────┐
T-7 (yml profile) ─────────┤
T-8 (V1 migration) ────────┤
T-9 (V2 seed) ─────────────┤
T-10 (JpaRoute entity) ────┤
T-11 (JpaPerson entity) ────┤
T-12 (JpaRoute repo) ───────┤
T-13 (JpaPerson repo) ───────┤
T-14 (PersistenceConfig) ───┤
T-15 (delete adapters) ─────┤
T-16 (DEC-2 fix) ────────────┼──► PR-2: feat/delivery-jpa-adapter-swap

T-17 (IntPersonsController)─┐
T-18 (IntRoutesController) ──┤
T-19 (FilterRegistration) ──┤
T-20 (IntPersonsControllerTest)─┤
T-21 (IntRoutesControllerTest)─┼──► PR-3: feat/delivery-internal-controllers
         ▲                    │
         │ (T-2 auto-config in PR-1 required for filter)
         └────────────────────┘

T-22 (JpaRouteRepositoryIT) ─┐
T-23 (JpaDeliveryPersonIT) ───┤
T-24 (DeliveryServiceAppIT) ──┤
T-25 (DeliveryMigrationIT) ───┤
T-26 (ArchitectureTest) ──────┤
T-27 (README/gateway docs) ──┤
T-28 (delete Supabase test) ─┤
T-29 (existing tests green) ──┼──► PR-4: chore/delivery-testcontainers-it-and-cleanup
         ▲                     │
         │ (T-12/T-13 JPA repos in PR-2 required for IT)
         └─────────────────────┘
```

---

## Skill Resolution
`paths-injected` — exact skill paths provided and loaded: `chained-pr/SKILL.md`, `work-unit-commits/SKILL.md`, `branch-pr/SKILL.md`.
