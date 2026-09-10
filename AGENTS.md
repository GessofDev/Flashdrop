# AGENTS.md — FlashDrop Backend coding standards

> Standards consumed by automated code review (Gentleman Guardian Angel) and by
> human contributors. Keep this file focused on **enforceable** conventions.

## 1. Commit conventions

- **Conventional Commits only**: `<type>(<scope>): <subject>`.
  - `type`: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`.
  - `scope`: service or area — `delivery`, `orders`, `catalog`, `auth`, `gateway`,
    `infra`, `floci`, `shared-observability`, or omit for repo-wide.
- **No AI attribution**: never add `Co-Authored-By:` lines or similar footers.
- **Subject in imperative mood**, ≤ 72 chars, no trailing period.
- **Body explains WHY, not WHAT** — the diff already shows what.

## 2. Service boundaries (do not cross)

- Each Spring Boot service owns its DB tables and its own DB user.
  See `infra/coolify/01-postgres-init.sql` for the canonical grants.
- Inter-service calls go through `/api/internal/*` with `X-Internal-Api-Key`.
- Services address each other by **Coolify resource name** (e.g.
  `http://flashdrop-orders:8083`), never by `localhost` or public URL.
- Do not reach into another service's DB or repository directly.

## 3. Build systems — mixed on purpose

- Java services use **Gradle (Kotlin DSL)**: `auth-service`, `catalog-service`,
  `delivery-service`, `shared-observability`.
- **`orders-service` uses Maven** (`mvnw`). Do not add it to `settings.gradle.kts`.
- Gateway is **pnpm 9 + TypeScript** (Fastify).

## 4. Code style

### Java (Spring Boot 3)

- Constructor injection. Mark the single constructor `@Autowired` when there are
  multiple constructors; never use field injection (`@Autowired` on a field).
- One public class per file, package-private visibility for test-only helpers.
- Records for DTOs and immutable value carriers.
- No silent fallbacks: if a parameter is accepted but ignored, the parameter
  must be removed. Misleading parameters are a bug, not a feature.
- Profile-based configuration: use `application-<profile>.yml` and
  `@ConditionalOnProperty` / `@Profile` annotations consistently.

### TypeScript (Gateway)

- Strict mode (`"strict": true`). No `any` except at JSON boundaries with a
  typed wrapper.
- Use `import type` for type-only imports.
- Prefer named exports.

### SQL / Flyway

- Migrations live under `src/main/resources/db/migration/`. Filenames:
  `V<n>__<description>.sql`. Never edit a merged file — add a new migration.

## 5. Tests

- Unit tests must not require Docker, network, or a real DB.
- Integration tests (`*IT.java`) require the test container and may be skipped
  locally; CI runs them.
- Test names should describe behavior, not implementation:
  `<methodOrBehavior>_<input>_<expectedOutcome>`.

## 6. PR / branch hygiene

- One branch per change. Name: `feat/<short-slug>`, `fix/<short-slug>`, or
  `chore/<short-slug>`.
- Rebase on `main` before opening the PR. Squash-merge only.
- Branch must include its own CI workflow under
  `services/<name>/.github/workflows/ci.yml` when introducing a new service.

## 7. What NOT to do

- Do not commit secrets, real DB passwords, or generated `.env` files.
  Use `infra/coolify/env.shared.template` as the source of truth.
- Do not reintroduce Supabase — it has been removed from `delivery-service`.
  Communicate with `orders-service` over HTTP via `HttpOrderServiceClientAdapter`.
- Do not add `Co-Authored-By: Claude` or similar AI-attribution trailers.
- Do not skip the pre-commit hook (`--no-verify`) to bypass review.