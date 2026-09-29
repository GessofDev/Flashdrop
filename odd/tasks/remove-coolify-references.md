# Feature: Remove Coolify references

## Goal
Eliminate every mention of Coolify from the active codebase. Coolify is no
longer the deployment target — the project now deploys via FloCI
(`infra/floci/INFRASTRUCTURE.md`).

## Scope (user-approved)
1. Archive `infra/coolify/` → `references/archived-coolify/`.
2. Clean "Coolify resource name" / "Coolify autogenera" comments in service
   configs (`application*.yml`, `docker-compose.stack.yml`). Defaults stay
   as-is (generic `auth-service` / `orders-service`, overridable by env vars
   at deploy time).
3. Rewrite `README.md`, `CLAUDE.md`, `AGENTS.md` end-to-end so architecture,
   service boundaries, and deploy sections point to FloCI instead of Coolify.
4. Leave `openspec/changes/*` untouched (archived SDD history; rewriting
   the past breaks traceability of decisions and dates).

## Out of scope
- FloCI docs / `INFRASTRUCTURE.md` content beyond removing the
  "Coolify-based deploy (production fallback)" line. FloCI has its own
  owner and shouldn't be edited in this pass.
- Service config defaults (`auth-service`, `orders-service`,
  `flashdrop-auth`) — these are not Coolify-specific, they're overridable
  in deploy. FloCI sets them via env vars from Secrets Manager.

## Tasks
See `todo` list. Mirror lives at `odd/remove-coolify-references/tasks/`
(via Engram `mem_save` at end of work).

## Evidence / commits
TBD — pending execution.

## Manual edit required (policy-blocked)
`services/orders-service/.env.example` lines 110-113 — replace with the
snippet stored at `odd/tasks/remove-coolify-references.md` §"Snippet for
.env.example" below. The change removes the mention of `01-postgres-init.sql`
and `Coolify` and points to `infra/floci/INFRASTRUCTURE.md` §4 instead.

## Snippet for .env.example

```diff
- #   2. Usuario de la BD: `orders_app` (NO `orders_svc` — el
- #      `01-postgres-init.sql` se escribió para Coolify y nunca corrió contra
- #      Floci, los grants están dados de otra forma). Confirmar contra la BD
- #      real antes de declarar el servicio operativo.
+ #   2. Usuario de la BD: `orders_app` (NO `orders_svc` — los grants se
+ #      dieron de otra forma en Floci, no via el script de bootstrap original;
+ #      ver `infra/floci/INFRASTRUCTURE.md` §4). Confirmar contra la BD real
+ #      antes de declarar el servicio operativo.
```
