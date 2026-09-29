# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Monorepo for **FlashDrop**, a delivery app. Result of refactoring a Node.js monolith into **4 Spring Boot 3 microservices + 1 Fastify/TypeScript API gateway**, deployed on [FloCI](infra/floci/INFRASTRUCTURE.md) (a local AWS emulator running on a VPS — RDS, ECS, Secrets Manager). The historical monolith and the original migration plan are kept under `references/` for context only — they are not part of active development.

## Services and ports

| Service | Port | Build | Owner of DB tables |
|---|---|---|---|
| `gateway` (Fastify) | 3000 | pnpm 9 / TypeScript | — |
| `services/auth-service` | 8081 | Gradle (Kotlin DSL) | users, login, roles, user_has_roles, refresh_tokens |
| `services/catalog-service` | 8082 | Gradle (Kotlin DSL) | categories, products, restaurant |
| `services/orders-service` | 8083 | **Maven** (`mvnw`) | orders, order_items, client |
| `services/delivery-service` | 8084 | Gradle (Kotlin DSL) | delivery, delivery_routes |
| `services/shared-observability` | — | Gradle module | — (logging, tracing, error catalog) |

Each service has its own Postgres database on FloCI's local RDS, with a least-privilege user per service (created via `aws --endpoint-url http://127.0.0.1:4566 rds ...` — see `infra/floci/INFRASTRUCTURE.md` §4).

## Big-picture architecture

```text
Mobile (Flutter) → Gateway (:3000) → Auth/Catalog/Orders/Delivery
                                       │
                  Inter-service calls via /api/internal/* with X-Internal-Api-Key
```

- The **gateway** is the only public entry point. Frontends never hit services directly.
- Services **own their data** and expose internal endpoints (`/api/internal/*`) for other services to read. No service reaches into another service's DB.
- **JWT** is issued by `auth-service` (RS256). Other services validate locally via the RSA public key — no round-trip to auth-service on each request.
- `shared-observability` is included as a Gradle dependency by services and auto-installs `CorrelationIdFilter` (propagates `X-Request-Id`), `ApiError`/`ErrorCatalog` (uniform `{status, error, message}` shape), `TraceContext` for structured logging, and `InternalApiKeyFilter` (validates `X-Internal-Api-Key` on `/api/internal/*`).
- All services speak to each other by **DNS inside the `floci_default` bridge** (e.g. `flashdrop-auth:8081`, `flashdrop-orders:8083`); the gateway/compose file wires those names to the right containers. Databases, by contrast, are reached via FloCI's local RDS endpoint (`172.16.1.2:<port>`).

## Inter-service auth — read this before touching internal endpoints

Every internal call MUST send `X-Internal-Api-Key: <shared-secret>`. The secret is identical across all 5 deployments and is stored as `flashdrop/internal-api-key` in FloCI's Secrets Manager. The header is validated by `shared-observability`'s `InternalApiKeyFilter` (or an equivalent per-service).

**Why a single shared secret**: if each service generates its own, the moment someone rotates one and not the others, every call to `/api/internal/*` returns 403 and the symptom doesn't point at the cause. There is exactly one secret, referenced by ARN across all task definitions.

## Build systems — mixed on purpose

`services/settings.gradle.kts` only includes the **Gradle** modules: `shared-observability`, `auth-service`, `catalog-service`, `delivery-service`. **`orders-service` is Maven and must be built separately** — do not try to add it to the Gradle root. This asymmetry is intentional and documented in the comments of `settings.gradle.kts`.

## Common commands

### Run a single service locally

```bash
# Auth / Catalog / Delivery (Gradle)
cd services/<name> && ./gradlew bootRun

# Orders (Maven)
cd services/orders-service && ./mvnw spring-boot:run

# Gateway
cd gateway && pnpm install && pnpm dev
```

### Tests

```bash
# Gateway (Vitest)
cd gateway && pnpm test                    # all
pnpm vitest run tests/unit/routing/matcher.test.ts   # single file
pnpm vitest run -t "matches longest prefix"          # by name
pnpm test:coverage                        # coverage report
pnpm lint && pnpm format                  # ESLint + Prettier

# Gradle services — run from services/<name>/
./gradlew test                            # full suite
./gradlew test --tests "com.flashdrop.*AuthServiceTest"  # single class
./gradlew compileTestJava                 # fast type-check

# Orders (Maven)
cd services/orders-service && ./mvnw test
./mvnw test -Dtest=OrdersControllerTest   # single class
./mvnw compile                             # fast type-check
```

### Local dependencies

Postgres + Redis run as plain Docker containers; schema bootstrap for local dev is `references/archived-coolify/01-postgres-init.sql` (creates `<servicio>_svc` users — only useful for the docker-compose setup; FloCI uses `<servicio>_app` users created via AWS CLI). Full recipe in `README.md` §"Desarrollo local".

## CI

Each service owns its own workflow under `services/<name>/.github/workflows/ci.yml` (the convention, even when a service doesn't have one yet — see `README.md` §CI/CD). The repo-root `.github/` only carries `CODEOWNERS`.

## Where to look for more

- **Gateway internals** (middleware, hot-reload, observability, JWT/JWKS, CORS, circuit breakers): `gateway/CLAUDE.md` — long, detailed, authoritative for that area.
- **Deploy to FloCI**: `infra/floci/INFRASTRUCTURE.md` — source of truth for the local environment (RDS instances, ECS task definitions, Secrets Manager layout, networking).
- **Historical Coolify artifacts** (do NOT derive decisions from these): `references/archived-coolify/` — the original 4-DB init script and Coolify deploy guide, kept around only because some local-dev defaults still reference the `<servicio>_svc` users it creates.
- **Public API contracts per service**: `<service>/openapi.yaml`.
- **Historical context** (do NOT derive decisions from this): `references/monolith/`, `references/migration-plan/`, `references/juniors-history/`.
