# CD pipeline hardening

Replace the six copy-pasted CD workflows with one reusable deploy workflow that
deploys the exact SHA that was tested, serializes deploys per service, gates on a
GitHub environment with approval, pins the SSH host key, applies/verifies DB
migrations on every deploy, and rolls back to the last good version when a gate
fails.

## Specs

S1. "que las migraciones se apliquen o se verifiquen en cada deploy, y que el health check lo detecte"

S2. "desplegar el SHA probado y no origin/main, agregar concurrency y agregar rollback a la versión anterior"

S3. "environment con aprobación y fingerprint SSH. Ideal en un workflow reutilizable único"

S4. "entiendo que son 3 problemas diferentes, por el momento me gustaria arreglar el cd"

S5. Non-goal (this feature): "F17: release y cancel-after-pickup piden el orderId como UUID, pero al repartidor le llega el numérico, así que la app recibe 400. Hay que hacer que acepten el numérico (como en el #62) o que delivery devuelva el UUID."

S6. Non-goal (this feature): "F18: probar el flujo de 6 códigos errados de punta a punta en el servidor, una vez que exista la tabla V4."

S7. Non-goal (this feature): "Rol SOPORTE: definir cómo se asigna, porque hoy solo se puede por el endpoint interno. Coordinarlo con Felipe (F6)."

S8. Non-goal (this feature): "HTTPS en el servidor (dominio más certificado). Sin eso no se publica en las tiendas ni se usa con clientes reales."

## Facts established by exploration (not new requirements)

- Six CD workflows exist under `.github/workflows/`: `auth-service-cd.yml`,
  `catalog-service-cd.yml`, `delivery-service-cd.yml`, `orders-service-cd.yml`,
  `gateway-cd.yml` (docker compose over SSH) and `web-cd.yml` (static bundle to
  FloCI S3 over SSH). None of them declares `environment:`, `concurrency:`,
  `workflow_call`, `permissions:`, a rollback step, or an SSH host key fingerprint.
- Every docker CD deploys with `git fetch origin main && git reset --hard origin/main`
  on the VPS: the code shipped is whatever `origin/main` points at when the SSH
  step runs, not the SHA that CI tested.
- `services/orders-service/src/main/resources/application.properties`:
  `spring.flyway.enabled=${FLYWAY_ENABLED:true}`; the stack passes
  `FLYWAY_ENABLED=${ORDERS_FLYWAY_ENABLED:?PENDIENTE Felipe - ver GAP-06 en application.properties}`
  (`gateway/docker/docker-compose.stack.yml:162`), so orders runs with Flyway off
  and `V4__order_delivery_operations.sql` is never applied.
- Orders migrations: `V1__init.sql`, `V2__persist_order_item_product_snapshot.sql`,
  `V3__delivery_code.sql`, `V4__order_delivery_operations.sql`. All of them are
  written idempotently (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`,
  `DROP CONSTRAINT IF EXISTS`).
- auth/catalog/delivery run Flyway app-side (`spring.flyway.enabled: true`,
  `ddl-auto: validate`); delivery uses schema `internal`. auth also ships a
  dev seed in `db/seed` that must not be applied by the deploy.
- Flyway version: orders `spring-boot-starter-parent 3.2.5`, gradle services
  Spring Boot `3.3.5` — both manage Flyway `9.22.3`.
- The database is only reachable from the `floci_default` network
  (`172.16.1.2:<port>`), which is an external Docker network on the server.

## Tasks

| ID | Spec | Task | Route | Commit |
|----|------|------|-------|--------|
| T1 | S3 | Create `.github/workflows/deploy.yml` as a reusable workflow (`on: workflow_call`): job `deploy` with `permissions: contents: read`, `environment: production`, `concurrency: group: deploy-${{ inputs.service }}` with `cancel-in-progress: false`, a guard step that fails when `VPS_SSH_FINGERPRINT` is empty, and the SSH step using `appleboy/ssh-action` with `fingerprint: ${{ secrets.VPS_SSH_FINGERPRINT }}`. Inputs: `service`, `script`, `health-url`, `migration-dir`, `migration-schema`, `compose-service`. | worker | `5a71c78` |
| T2 | S2, S1 | Add `gateway/docker/scripts/deploy-service.sh`: resolve the repo dir on the VPS, `git fetch origin <sha>` + `git checkout --force --detach <sha>`, keep the last good SHA in `~/.flashdrop-deploy/<service>.sha`, run the migration step, `docker compose up -d --no-deps --build <service>`, wait for the health URL, verify migrations, and on any failure roll back to the stored SHA (rebuild + up + health wait) before exiting non-zero. | parent | `f3bd1ed`, `beb1b40` |
| T3 | S1 | Add `gateway/docker/scripts/migrate.sh` plus per-service configuration: read `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` from the compose environment, run the Flyway CLI (`flyway/flyway:9.22.3-alpine`) on the `floci_default` network with `-baselineOnMigrate=true -baselineVersion=1`, `locations=filesystem:/migrations`, and `-schemas=internal` for delivery only; `apply` mode migrates, `verify` mode fails when a migration file in the repo is not recorded as applied. | parent | `f3bd1ed`, `beb1b40` |
| T4 | S2 | Refactor `auth-service-cd.yml`, `catalog-service-cd.yml`, `delivery-service-cd.yml`, `orders-service-cd.yml` and `gateway-cd.yml` to call the reusable workflow with their per-service inputs and delete the duplicated inline `script:` blocks. | worker | `5a71c78` |
| T5 | S3 | Update `web-cd.yml` with the same environment gate, concurrency group and SSH fingerprint, keeping the S3 sync deploy. | worker | `5a71c78` |
| T6 | S1, S2 | Add shell tests for the deploy and migration scripts under `gateway/docker/scripts/tests/` using fake `git`, `docker` and `curl` binaries on `PATH` (no Docker, no DB, no network) and run them from CI. | parent | `f3bd1ed`, `beb1b40` |
| T7 | S3, S1, S2 | Document the deploy runbook: required secrets (`VPS_SSH_FINGERPRINT` and how to compute it), the GitHub environment `production` and its required reviewers, the migration policy, and the rollback procedure. | worker | `docs(cd): document the deploy runbook` |
| T8 | S1, S2, S3 | Independent verification of the high-risk migration item: check that a deploy applies missing migrations, that a failing health check rolls back to the previous SHA, and that no deploy pulls `origin/main` instead of the tested SHA. | parent (verifier unavailable) | see L3 |

## Log

L1. User request (verbatim):

> un dev me dice esto: Confirmar que en el servidor exista la tabla order_delivery_operations (migración V4 de orders). Orders corre con Flyway apagado, así que si la tabla falta hay que aplicarla. Sin ella fallan F17 y F18.
> F17: release y cancel-after-pickup piden el orderId como UUID, pero al repartidor le llega el numérico, así que la app recibe 400. Hay que hacer que acepten el numérico (como en el #62) o que delivery devuelva el UUID.
> F18: probar el flujo de 6 códigos errados de punta a punta en el servidor, una vez que exista la tabla V4.
> Rol SOPORTE: definir cómo se asigna, porque hoy solo se puede por el endpoint interno. Coordinarlo con Felipe (F6).
> CD: que las migraciones se apliquen o se verifiquen en cada deploy, y que el health check lo detecte.
> CD: desplegar el SHA probado y no origin/main, agregar concurrency y agregar rollback a la versión anterior.
> CD: environment con aprobación y fingerprint SSH. Ideal en un workflow reutilizable único.
> HTTPS en el servidor (dominio más certificado). Sin eso no se publica en las tiendas ni se usa con clientes reales.
>
> entiendo que son 3 problemas diferentes, por el momentyo me gustaria arreglar el cd

L2. Scope decision (parent, exploration): the CD work owns the deploy pipeline only.
The Flyway flag for orders (`ORDERS_FLYWAY_ENABLED`) stays as-is — it is an open
infrastructure decision ("PENDIENTE Felipe") — so the deploy applies and verifies
migrations itself with the same Flyway version the services use.

L3. Verification evidence (parent): the migration path was exercised against a
real PostgreSQL 15 with the real scripts, not only with the fakes. `migrate.sh
apply` baselined the schema at V1 and applied V2 to V5, `order_delivery_operations`
existed afterwards, a second run was a no-op, and `migrate.sh verify` failed with
"the database of orders is missing the migrations:
V4__order_delivery_operations.sql" when the history was missing V4. The deploy
logic itself was exercised with the fake binaries (`bash
gateway/docker/scripts/tests/run-tests.sh`, 25 assertions) and the rollback was
confirmed to fail the test suite when it is broken.

L4. Defects found by that verification and fixed in `beb1b40`: the Flyway CLI was
receiving a `postgresql://` URL (Flyway 9 only understands the `jdbc:` form and
answered "No database found to handle postgresql://...") and the baseline was
requested through `FLYWAY_BASELINEONMIGRATE` (Flyway 9 ignores that name; the
CLI flags `-baselineOnMigrate=true -baselineVersion=1` are the ones that work).
The gateway also had no row in the service configuration and failed before
doing anything.

L5. Independent verification: the `gentle-ai-verify` subagent was unavailable
(the run failed before any probe), so the high-risk item was verified inline
against a real database instead. The checks that remain for a human: the deploy
itself has never run against the real VPS, and the `production` environment with
its required reviewers has to be configured in the GitHub settings before the
first deploy.

L6. Risk: item 1 — the deploy applies DDL to the database of orders
(`order_delivery_operations` and the other pending migrations). The migrations
are additive and are never reverted by a rollback, so the schema can end up one
step ahead of the code.

L7. Decision (human): the GitHub environment is called `dev`, not `production`,
because there is no production environment yet — the only deploy target is the
development VPS. The environment name became an input of the reusable workflow
(`environment`, default `dev`) and of the web workflow (dispatch input), so
promoting to the real production is a parameter and not a name hardcoded in
six files. The name of the environment does not create any infrastructure: it
is a label with approval rules attached to a deploy target.

L8. Decision (human): in the current development stage the deploy stays
automatic on every push to `main`. The `dev` environment therefore ships
WITHOUT required reviewers, so the deploy does not wait for a human. The
approval gate stays wired but lives in the repository settings: turning it on
is `Settings -> Environments -> dev -> Required reviewers`, a settings change
with no code and no release, to be flipped when the v1 of the product starts
requiring it. The environment also records the deployment history from the
first deploy, which is what the future `production` promotion will need. The
human is the repository admin, so the rules can be configured whenever they
are needed.

L9. Server preflight (human, from the VPS console): the deploy target is
`76.13.168.23` and its ED25519 host key fingerprint is
`SHA256:yNPvpXWKCguBPf47pXLYIQp/mWT94HUQKStkN52y7eA`, confirmed three ways
(network `ssh-keyscan`, the server's own `/etc/ssh/ssh_host_ed25519_key.pub`
and `sshd -T | grep hostkey`). The repository on the server is
`/home/dev/Flashdrop` (the workflow's fallback chain covers it), the stack
`.env` exists at `gateway/docker/.env` with `ORDERS_FLYWAY_ENABLED`,
`ORDERS_DB_PASSWORD`, `DELIVERY_DB_PASSWORD` and `INTERNAL_API_KEY` defined,
the running containers are named `stack-auth`, `stack-catalog`, `stack-orders`,
`stack-delivery` and `stack-gateway` (exactly what the deploy configuration
expects), the `floci_default` network exists, and the clone is not shallow with
`origin` pointing at `https://github.com/GessofDev/Flashdrop.git`. The SSH
user has to be `dev`, the owner of the repository and of the `.env`.
