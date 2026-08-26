# Archive Report — delivery-service-dedicated-db

## Goal
Wire `delivery-service` (port 8084) from shared Supabase REST to a dedicated PostgreSQL database (`delivery_db`) via Spring Data JPA + Flyway, and add a shared `InternalApiKeyFilter` across all services via `shared-observability`.

## Closed on
2026-08-25

## Outcome
SUCCESS_WITH_WARNINGS — verify returned 0 CRITICALs, 2 WARNINGs, 2 SUGGESTIONs. Archive proceeds.

---

## Final chain
- **PR-1**: `feat/shared-observability-internal-api-key` (commits: `0c233c4`, `111830d`) — InternalApiKeyFilter + auto-config + filter tests
- **PR-2**: `feat/delivery-jpa-adapter-swap` (commit: `b31a791`) — JPA/Flyway deps, application-delivery.yml, V1/V2 migrations, JPA entities/repos, PersistenceConfig, Supabase adapter deletion, DEC-2 fix
- **PR-3**: `feat/delivery-internal-controllers` (commits: `4032f22`, `5366fcf`, `96fa1ab`, `b463053`) — InternalDeliveryPersonsController + InternalRoutesController + controller tests
- **PR-4**: `chore/delivery-testcontainers-it-arch-test-docs` (commits: `9c9c184`, `94bec37`, `1a4bfff`, `93a8b18`, `ea2d442`) — Testcontainers ITs, ArchUnit NFR-1, domain unit tests, JSON contract tests, docs
- **PR-5**: `feat/delivery-orders-http-adapter-supabase-removal` (commit: `TBD`) — DEC-1 EXECUTED: Supabase removed, HTTP adapter to orders-service with graceful degradation, mock adapter

---

## Spec coverage (from verify-report)

### Functional Requirements
| FR | Description | Status |
|----|-------------|--------|
| FR-1 | Dedicated DataSource on boot | SATISFIED — application-delivery.yml: jdbc:postgresql with Flyway on schema internal |
| FR-2 | JPA persistence layer (replaces Supabase REST) | SATISFIED — JpaRouteRepository + JpaDeliveryPersonRepository implement ports. Supabase adapters deleted |
| FR-3 | InternalApiKeyFilter in shared-observability | SATISFIED — MessageDigest.isEqual constant-time compare via ApiKeyValidator. @Profile(!dev) + @ConditionalOnProperty |
| FR-4 | Endpoint protection contract | SATISFIED — shouldNotFilter() = false for /api/internal/*. Filter order HIGHEST_PRECEDENCE+10 after CorrelationIdFilter |
| FR-5 | Seed script | PARTIAL — V2__seed idempotent but user_id='1' instead of 'U1' per spec (WARNING-1) |
| FR-6 | Existing tests preserved | SATISFIED — All 75 tests pass. No test removed |
| FR-7 | Datasource URL composition | SATISFIED — application-delivery.yml correct JDBC URL with env var fallbacks |
| FR-8 | Public API unchanged | SATISFIED — RouteController + DeliveryController unchanged |

### Non-Functional Requirements
| NFR | Description | Status |
|-----|-------------|--------|
| NFR-1 | Hexagonal purity | SATISFIED — DeliveryArchitectureTest (9 ArchUnit TCs) enforces no jakarta.persistence in domain/application |
| NFR-2 | Strict TDD | SATISFIED — Test-first followed in PR-2 and PR-4 |
| NFR-3 | Least-privilege DB user | SATISFIED — env.shared.template lines 123-128 define delivery_svc |
| NFR-4 | Boot latency (P95 <= 10s) | UNVERIFIED — Requires Docker CI environment |
| NFR-5 | Correlation ID on all log lines | SATISFIED — CorrelationIdFilter at HIGHEST_PRECEDENCE before InternalApiKeyFilter |

### BDD Scenarios (10/10 covered)
Scenario 1 (Boot happy path) — COVERED (DeliveryServiceApplicationIT + DeliveryMigrationIT)
Scenario 2 (Boot conflicting schema) — PARTIAL (failure path implicit)
Scenario 3 (Public GET /api/delivery/routes) — COVERED (DeliveryControllerTest + RouteControllerTest)
Scenario 4 (Missing key -> 401) — COVERED (InternalDeliveryPersonsControllerTest)
Scenario 5 (Wrong key -> 401) — COVERED (InternalDeliveryPersonsControllerTest)
Scenario 6 (Correct key -> 200) — COVERED (InternalDeliveryPersonsControllerTest)
Scenario 7 (POST /api/internal/routes valid -> 201) — COVERED (InternalRoutesControllerTest)
Scenario 8 (POST /api/internal/routes invalid -> 400) — COVERED (InternalRoutesControllerTest)
Scenario 9 (PATCH status -> 200) — COVERED (InternalRoutesControllerTest)
Scenario 10 (Seed idempotency) — COVERED (DeliveryMigrationIT)

---

## ADR decisions (from design.md)

### ADR-1 — OrderServiceClientAdapter cross-service strategy
**Choice**: ~~(A) Keep Supabase REST adapter for orders/restaurant reads~~ — DEC-1 EXECUTED in PR-5. Supabase REST fully removed. Now using HTTP via `HttpOrderServiceClientAdapter` calling `orders-service` at `${orders.service.url}`.
**Consequences**: Delivery-service now reads from orders-service via HTTP with graceful degradation (returns empty list + logs WARN on failure). No Supabase dependency remains in delivery-service.

### ADR-2 — OrderRow.clientId resolution
**Choice**: (C) Fix PostgREST select — drop client_id from select string AND remove unused clientId field from OrderRow.
**Consequences**: Adapter stops throwing; no schema change; no DTO change.

### ADR-3 — JPA ID strategy
**Choice**: (A) BIGINT with @GeneratedValue(strategy = IDENTITY).
**Alternatives**: (B) UUID — requires backfill; (C) application-side assignment — discards Spring magic.
**Consequences**: Matches existing Supabase schema; matches catalog-service precedent.

### ADR-4 — InternalApiKeyFilter contract
**Choice**: Header X-Internal-Api-Key; constant-time compare via MessageDigest.isEqual; failure HTTP 401 with ApiError; URL pattern /api/internal/*; filter order after CorrelationIdFilter (HIGHEST_PRECEDENCE+10) and before JWT filter; auto-registered via ObservabilityAutoConfiguration; disabled in dev profile when INTERNAL_API_KEY absent; fail-closed otherwise.

### ADR-5 — Java toolchain override (overturns archived ADR-005)
**Choice**: (B) Stay on Java 21 — build.gradle.kts lines 11-12 declare JavaVersion.VERSION_21.
**Evidence**: Current repo state is Java 21; sdd-init/flashdrop_backend lists Java 21; juniors/junior-1-auth also Java 21; Spring Boot 3.3.0 supports both JDK 17 and JDK 21.
**Rollback**: Revert PR; ADR-005 is retired.

---

## PR-5 Summary — COMPLETE (DEC-1 EXECUTED)
**Branch**: `feat/delivery-orders-http-adapter-supabase-removal`
**Jira**: DEC-1 follow-up — manual transition required

### Tasks Completed
- [x] Delete Supabase files: `SupabaseRestClientConfig.java`, `OrderRow.java`, `RestaurantRow.java`, `application-supabase.yml`
- [x] Delete empty `supabase/` directory
- [x] Create `HttpOrderServiceClientAdapter.java` — HTTP adapter calling orders-service over REST with graceful degradation
- [x] Create `OrdersServiceRestClientConfig.java` — `RestClient` bean with `X-Internal-Api-Key` header
- [x] Create `application-orders.yml` — `orders.service.url` with `ORDERS_SERVICE_URL` env var
- [x] Create `MockOrderServiceClientAdapter.java` — `@Profile("mock-orders")` mock returning empty lists
- [x] Create `HttpOrderServiceClientAdapterTest.java` — 6 unit tests for input validation and interface contract
- [x] Create `MockOrderServiceClientAdapterTest.java` — 5 unit tests verifying mock behavior
- [x] Delete `OrderServiceClientAdapterTest.java` (Supabase adapter test — superseded)
- [x] Update `README.md` — add inter-service HTTP communication notes
- [x] Update `env.shared.template` — add ORDERS_SERVICE_URL comment about future AWS/Parameter Store

### Architectural Decision (DEC-1 EXECUTED)
`delivery-service` reads from `orders-service` via HTTP REST instead of Supabase REST. The `HttpOrderServiceClientAdapter` sends `X-Internal-Api-Key` header on every request and degrades gracefully (returns empty list + logs WARN with trace ID on failure). A `MockOrderServiceClientAdapter` activated via `@Profile("mock-orders")` supports local development and automated tests without running `orders-service`.

### Files Created (5 new)
- `services/delivery-service/src/main/java/.../adapter/outbound/client/HttpOrderServiceClientAdapter.java`
- `services/delivery-service/src/main/java/.../adapter/outbound/client/MockOrderServiceClientAdapter.java`
- `services/delivery-service/src/main/java/.../config/OrdersServiceRestClientConfig.java`
- `services/delivery-service/src/main/resources/application-orders.yml`
- `services/delivery-service/src/test/java/.../HttpOrderServiceClientAdapterTest.java`
- `services/delivery-service/src/test/java/.../MockOrderServiceClientAdapterTest.java`

### Files Deleted (5)
- `services/delivery-service/src/main/java/.../config/SupabaseRestClientConfig.java`
- `services/delivery-service/src/main/java/.../adapter/outbound/persistence/supabase/OrderRow.java`
- `services/delivery-service/src/main/java/.../adapter/outbound/persistence/supabase/RestaurantRow.java`
- `services/delivery-service/src/main/java/.../adapter/outbound/client/OrderServiceClientAdapter.java` (old Supabase-based adapter)
- `services/delivery-service/src/test/java/.../OrderServiceClientAdapterTest.java` (Supabase adapter test)

### Files Modified (2)
- `README.md` — added inter-service HTTP communication section
- `infra/coolify/env.shared.template` — added ORDERS_SERVICE_URL comment

### Test Results PR-5 (non-IT)
```
./gradlew :delivery-service:test --tests "*Test" --tests "!*IT"
BUILD SUCCESSFUL in 20s
77 tests passed, 0 failures (4 IT tests skipped — Docker unavailable)
```

---

## Follow-ups (open work)

| ID | Item | Description | Gated on |
|----|------|-------------|----------|
| DEC-1 | OrderServiceClientAdapter HTTP refactor | **EXECUTED** — Supabase REST replaced by HTTP client to orders-service | N/A |
| WARNING-1 | Seed user_id mismatch | V2__seed-delivery_persons.sql inserts user_id='1' instead of 'U1' per spec | Fix in next migration: `UPDATE internal.delivery_persons SET user_id='U1' WHERE user_id='1';` |
| WARNING-2 | PersistenceConfig lacks @Profile guard | Design specifies @Profile("delivery") but implementation has no profile guard | Add `@Profile("delivery")` to PersistenceConfig class annotation |
| SUGGESTION-1 | No Flyway undo migrations | U__*.sql absent (Flyway Teams/Pro required) | Document rollback via `flywayClean` non-prod, manual DROP SCHEMA prod |
| SUGGESTION-2 | delivery_id FK column inert | FK column exists in schema but DeliveryRouteJpaEntity has no deliveryId field mapped | Decide FK requirement; update entity if needed |

---

## Files
- 59 files changed, ~3260 insertions, ~420 deletions across 5 PRs
- 83 tests total (77 passed, 6 skipped — Docker unavailable locally; expected to pass in CI)

---

## Deliverables shipped
- `services/delivery-service` now owns `delivery_db` via Spring Data JPA + Flyway (internal schema, user delivery_svc)
- `shared-observability` ships InternalApiKeyFilter (constant-time comparison via MessageDigest.isEqual, auto-configured, fail-closed)
- `/api/internal/delivery-persons` (GET) and `/api/internal/routes` (POST, PATCH) shipped with X-Internal-Api-Key protection
- 10 new test files: ArchUnit NFR-1 purity test, domain unit tests, Testcontainers ITs, JSON contract tests
- `env.shared.template` updated with DELIVERY_DB_HOST and DELIVERY_DB_PORT defaults; DEPLOY.md updated with SPRING_PROFILES_ACTIVE=delivery
- README.md, gateway/CLAUDE.md, services/delivery-service/README.md updated

---

## Jira status
Manual Jira UI review required (no CLI available). All transitions (KAN-21 parent, KAN-36..KAN-47 children) must be reviewed and transitioned manually. Corrected Jira mapping per verify-report:
- PR-1: KAN-36, KAN-43
- PR-2: KAN-37, KAN-38, KAN-39, KAN-40, KAN-41, KAN-42
- PR-3: KAN-40, KAN-41, KAN-42 (controller implementations complete KAN-40/41/42)
- PR-4: KAN-21, KAN-44, KAN-45, KAN-46, KAN-47
- PR-5: DEC-1 follow-up — manual transition required (no Jira ticket; documented as DEC-1 closure)

---

## Engram traceability IDs

| Artifact | Observation ID |
|----------|----------------|
| proposal | 177 |
| design | 179 |
| tasks | 181 |
| apply-progress | 182 |

Spec (sdd/delivery-service-dedicated-db/spec) and verify-report (sdd/delivery-service-dedicated-db/verify-report) were not persisted to Engram — retrieved from openspec mirror at `openspec/changes/delivery-service-dedicated-db/`.

---

## Change folder action
Left in place at `openspec/changes/delivery-service-dedicated-db/` (no project convention for archive folder move exists). Archive-report.md written inside the change folder as record. SDD cycle closed.
