# Spec: `delivery-service-dedicated-db`

## 1. Overview

This spec formalises the requirements for wiring `delivery-service` (port 8084) to its own dedicated PostgreSQL database (`delivery_db`) and adding a shared internal-API-key security filter via `shared-observability`. The public REST contract is unchanged; this is a pure implementation migration within the existing hexagonal architecture.

---

## 2. Functional Requirements

### FR-1 — Dedicated DataSource on boot
`delivery-service` connects to `delivery_db` on startup using the `delivery` Spring profile. The `DataSource` is composed from env vars `DELIVERY_DB_HOST` + `DELIVERY_DB_PORT` / `DELIVERY_DB_NAME` with user `delivery_svc`. On boot the service runs Flyway migrations from `src/main/resources/db/migration/` and fails fast (non-zero exit) if the schema is missing or inconsistent.

### FR-2 — JPA persistence layer (replaces Supabase REST adapters)
Two Spring Data JPA repositories (`JpaRouteRepository`, `JpaDeliveryPersonRepository`) implement the existing outbound port interfaces (`RouteRepository`, `DeliveryPersonRepository`) in `application/port/outbound/`. The Supabase REST adapters (`SupabaseRestRouteRepository`, `SupabaseRestDeliveryPersonRepository`) are retired and removed. The swap is transparent to domain and application layers.

### FR-3 — InternalApiKeyFilter in shared-observability
`InternalApiKeyFilter` lives in `services/shared-observability/src/main/java/com/flashdrop/observability/security/`. It validates the `X-Internal-Api-Key` request header using `MessageDigest.isEqual` (constant-time) against the value of the `INTERNAL_API_KEY` env var. On mismatch or absence it responds with HTTP **401** and the project's standard `ApiError` body shape (`code="UNAUTHORIZED"`, `message="Missing or invalid internal API key"`). The filter is registered via `ObservabilityAutoConfiguration` so every service that includes the module inherits it automatically.

### FR-4 — Endpoint protection contract
| Pattern | Auth |
|---|---|
| `/api/delivery/routes` (GET) | Public — no auth |
| `/api/delivery/claim` (POST) | Public — no auth (gateway only) |
| `/api/internal/delivery-persons` | `X-Internal-Api-Key` required |
| `/api/internal/routes` | `X-Internal-Api-Key` required |
| `/api/internal/routes/{orderId}/status` | `X-Internal-Api-Key` required |

The filter executes after `CorrelationIdFilter` and before any JWT/auth filter. In the `dev` Spring profile the filter is disabled when `INTERNAL_API_KEY` is absent (explicit opt-out); absence of the env in non-dev profiles keeps the filter active (fail-closed).

### FR-5 — Seed script
A Flyway-undoable seed script (`V2__seed-delivery_persons.sql`) populates `delivery_db` with the minimum fixtures required for the service to boot, serve a happy-path `GET /api/delivery/routes`, and pass all existing unit tests. The script is idempotent: re-running it is a no-op (uses `INSERT … ON CONFLICT DO NOTHING` or equivalent guard).

### FR-6 — Existing tests preserved
All 7 existing test files / 19 test methods continue to pass, or are migrated (not deleted), under the same runner (`delivery-service` Gradle target). No test is removed as a shortcut; if a test targets a retired adapter it is replaced by an equivalent Testcontainers-backed JPA integration test.

### FR-7 — Datasource URL composition
```
jdbc:postgresql://${DELIVERY_DB_HOST:localhost}:${DELIVERY_DB_PORT:5432}/${DELIVERY_DB_NAME}
```
Defaults: `DELIVERY_DB_HOST` falls back to `POSTGRES_HOST`; `DELIVERY_DB_PORT` defaults to `5432`; `DELIVERY_DB_NAME` is `delivery`.

### FR-8 — Public API contract unchanged
Paths, HTTP methods, response envelope (`ApiResponse<T>`), status codes, and `code` field semantics of every `/api/delivery/*` endpoint are identical to the prior archive (`consolidate-delivery-into-delivery-service`). No change to the gateway routing or to any client-facing documentation.

---

## 3. Non-Functional Requirements

### NFR-1 — Hexagonal purity
Zero imports of `jakarta.persistence`, `org.springframework.data`, or `org.springframework.boot` in packages `domain/` or `application/`. Enforceable via an architecture unit test (e.g. an `ArchTest` JUnit rule or a grep-based pre-commit hook).

### NFR-2 — Strict TDD
Every new test file is written RED before the corresponding production class is created. The new `JpaRouteRepositoryIT` (Testcontainers, `@Testcontainers`, JUnit 5) is the first test that exercises the JPA adapter; it must be committed alongside the entity/repository but before the controller wiring is complete.

### NFR-3 — Least-privilege DB user
The `delivery_svc` PostgreSQL user has **no** `SUPERUSER`, `CREATEDB`, or `CREATEROLE` privileges. Verification: `psql -c '\dp' delivery_db.*` shows `delivery_svc` granted only `CONNECT` on the database and `SELECT/INSERT/UPDATE/DELETE` on the two application schemas (`internal.delivery`, `internal.delivery_routes`).

### NFR-4 — Boot-to-ready latency
P95 time from process start to `GET /actuator/health` returning `UP` must be ≤ **10 seconds** in the CI environment (Cold start, no prior container image layer cached for JPA metadata). Flyway migration execution is included in this budget.

### NFR-5 — Correlation ID on all log lines
Every log line emitted during boot and every request handling cycle carries the `X-Request-Id` value from `TraceContext` (already implemented by `CorrelationIdFilter`). This is a pre-existing requirement that must not be broken by the new components.

---

## 4. BDD Scenarios

### Scenario 1 — Boot happy path with empty DB
**FR:** FR-1, FR-5  
**Given** `delivery_db` exists and is empty  
**When** `delivery-service` starts with profile `delivery` and valid env vars  
**Then** Flyway runs `V1__create-tables.sql` and `V2__seed-delivery_persons.sql` without error; `/actuator/health` returns `{"status":"UP"}`; a `delivery_persons` row is present in the DB.

### Scenario 2 — Boot with conflicting schema (Flyway error)
**FR:** FR-1  
**Given** `delivery_db` contains a table `internal.delivery_routes` with an incompatible column  
**When** `delivery-service` starts with profile `delivery`  
**Then** Flyway aborts with a descriptive error; the JVM process exits with a non-zero code; a clear error mentioning `delivery_db` connection or schema mismatch appears in the logs.

### Scenario 3 — GET /api/delivery/routes without internal key (public route)
**FR:** FR-4, FR-8  
**Given** the service is running with valid DB  
**When** a client sends `GET /api/delivery/routes` with no `X-Internal-Api-Key` header  
**Then** the response is `200 OK` with a valid `ApiResponse<List<DeliveryRoute>>` body (unchanged contract).

### Scenario 4 — GET /api/internal/delivery-persons without key
**FR:** FR-3, FR-4  
**Given** the service is running  
**When** a client sends `GET /api/internal/delivery-persons?userId=X` with no `X-Internal-Api-Key` header  
**Then** the response is `401 Unauthorized` with body matching the `ApiError` shape: `{"code":"UNAUTHORIZED","message":"Missing or invalid internal API key"}`.

### Scenario 5 — GET /api/internal/delivery-persons with wrong key
**FR:** FR-3, FR-4  
**Given** the service is running with `INTERNAL_API_KEY=secret`  
**When** a client sends `GET /api/internal/delivery-persons?userId=X` with header `X-Internal-Api-Key: wrong-value`  
**Then** the response is `401 Unauthorized` with the same `ApiError` body as Scenario 4.

### Scenario 6 — GET /api/internal/delivery-persons with correct key
**FR:** FR-3, FR-4  
**Given** the service is running with `INTERNAL_API_KEY=correct-secret`; a `delivery_persons` row exists for user `U1`  
**When** a client sends `GET /api/internal/delivery-persons?userId=U1` with header `X-Internal-Api-Key: correct-secret`  
**Then** the response is `200 OK` with `ApiResponse<DeliveryPerson>` body; the `id` field equals the DB row; `userId` equals `U1`.

### Scenario 7 — POST /api/internal/routes with correct key and valid body
**FR:** FR-2, FR-4, FR-8  
**Given** the service is running with a valid seed; `INTERNAL_API_KEY=correct-secret`  
**When** a client sends `POST /api/internal/routes` with header `X-Internal-Api-Key: correct-secret` and a valid `CreateDeliveryRouteRequest` body  
**Then** the response is `201 Created` with `ApiResponse<DeliveryRoute>`; a row exists in `internal.delivery_routes` with the submitted `orderIds`.

### Scenario 8 — POST /api/internal/routes with correct key and invalid body
**FR:** FR-2, FR-4  
**Given** the service is running  
**When** a client sends `POST /api/internal/routes` with header `X-Internal-Api-Key: correct-secret` and a body missing required fields  
**Then** the response is `400 Bad Request` with an `ApiError` shape describing the validation failure.

### Scenario 9 — PATCH /api/internal/routes/{orderId}/status with correct key
**FR:** FR-2, FR-4, FR-8  
**Given** a `delivery_routes` row exists with `orderId=ORD-1`  
**When** a client sends `PATCH /api/internal/routes/ORD-1/status` with header `X-Internal-Api-Key: correct-secret` and body `{"status":"IN_PROGRESS"}`  
**Then** the response is `200 OK`; the DB row is updated; subsequent `GET /api/delivery/routes` reflects the new status.

### Scenario 10 — Seed run twice is idempotent
**FR:** FR-5  
**Given** `V2__seed-delivery_persons.sql` has already run  
**When** Flyway runs `V2__seed-delivery_persons.sql` a second time (e.g. after a failed undo and re-apply)  
**Then** no duplicate rows are inserted; the operation completes without error.

---

## 5. Open Decisions (NOT resolved here — passed to sdd-design)

### DEC-1 — `OrderServiceClientAdapter` cross-service reads
| Option | Tradeoff |
|---|---|
| **(A) Keep Supabase REST for `orders`/`restaurant`** | Smaller blast radius; delivery-service still couples to shared DB. |
| **(B) Refactor to HTTP call to `orders-service`** | True isolation; adds network failure mode; requires orders-service endpoint. |

### DEC-2 — `OrderRow.clientId` resolution
| Option | Tradeoff |
|---|---|
| **(A) Drop the field** | Removes the bug; updates `OrderInfo` DTO. |
| **(B) Add `client_id` column to `orders` table** | Preserves API; schema change belongs to orders-service. |
| **(C) Fix PostgREST select to not request `client_id`** | Minimal code change; treats missing column as known. |

### DEC-3 — JPA ID strategy
| Option | Tradeoff |
|---|---|
| **(A) `BIGINT` + `@GeneratedValue(strategy=IDENTITY)`** | Matches existing Supabase schema; matches `orders-service` precedent. |
| **(B) UUID + `@GeneratedValue`** | Globally unique; requires backfill column. |
| **(C) Explicit application-side assignment** | Deterministic; awkward for tests. |

### DEC-4 — `InternalApiKeyFilter` detailed contract
| Question | Options |
|---|---|
| Header name | `X-Internal-Api-Key` (proposed, constant) |
| Comparison | `MessageDigest.isEqual` (proposed, constant-time) |
| Failure body | `ApiError(code="UNAUTHORIZED", message="Missing or invalid internal API key")` |
| Filter order | After `CorrelationIdFilter`, before auth filters (proposed) |
| Auto-activation | Via `ObservabilityAutoConfiguration` (proposed) |
| Dev profile | Filter disabled when `INTERNAL_API_KEY` absent (proposed) |

---

## 6. Acceptance Criteria Checklist

| ID | Criterion | FR |
|---|---|---|
| AC-1 | `./gradlew :delivery-service:bootJar` succeeds with profile `delivery` | FR-1 |
| AC-2 | Container boots against `delivery_db`; Flyway runs both migrations; `/actuator/health` returns `UP` | FR-1, FR-5 |
| AC-3 | All 19 existing unit tests pass without modification | FR-6 |
| AC-4 | New `JpaRouteRepositoryIT` (Testcontainers) passes | FR-2 |
| AC-5 | `POST /api/delivery/claim` + `GET /api/delivery/routes` round-trip a `delivery_routes` row through JPA with unchanged response shape | FR-2, FR-8 |
| AC-6 | Request without `X-Internal-Api-Key` on `/api/internal/*` returns `401` with correct `ApiError` | FR-3, FR-4 |
| AC-7 | Request with wrong key on `/api/internal/*` returns `401` with correct `ApiError` | FR-3, FR-4 |
| AC-8 | Gateway routes `/api/delivery` to `delivery-service:8084` unchanged | FR-8 |
| AC-9 | No `jakarta.persistence`, `org.springframework.data`, or `org.springframework.boot` import in `domain/` or `application/` | NFR-1 |
| AC-10 | Every new test file is RED before corresponding production code | NFR-2 |
| AC-11 | `delivery_svc` has no superuser privileges (`\dp` check) | NFR-3 |
| AC-12 | P95 boot-to-ready ≤ 10 s in CI | NFR-4 |
| AC-13 | All log lines carry `X-Request-Id` | NFR-5 |
