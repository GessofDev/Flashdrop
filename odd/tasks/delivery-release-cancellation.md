# Feature: delivery release and post-pickup cancellation

Feature specification for the courier release flow and the authorized cancellation flow for orders already collected. Backend and API only; no web UI changes are included.

## Specs

S1 — Título
> El repartidor no puede soltar un pedido que tomó

S2 — Descripción
> No hay endpoint para liberar ni reasignar un pedido (el claim solo toma pedidos sin asignar). Si el repartidor no puede ir, el pedido queda asignado y nadie más lo puede tomar. Definir además un procedimiento autorizado para pedidos ya retirados, como FD-65, con motivo y trazabilidad.

S3 — Alcance
> Solo API y backend

S4 — Autorización
> Repartidor y Soporte

S5 — Estado final posterior al retiro
> CANCELADO o RECHAZADO a elección

S6 — Motivo
> Motivo obligatorio siempre

S7 — Orquestación
> Orquestar desde Orders (recomendado)

## Tasks

T1 | S1-S7 | inline | `docs(design): document delivery release and cancellation` (`2a6b9e3`)
T2 | S4 | inline | `feat(auth): add support role` (`2fe5c35`)
T3 | S5-S6 | worker | `feat(orders): add durable delivery operations`
T4 | S1,S6 | worker | `feat(orders): release assigned orders before pickup`
T5 | S4-S6 | worker | `feat(orders): cancel picked-up orders with authorized support`
T6 | S1,S5 | inline | `feat(delivery): add conditional route release and cancellation` (`6038b62`); `feat(delivery): expose internal route release endpoint` (`6bc46fc`)
T7 | S2,S6 | worker | `feat(orders): expose delivery operation history`
T8 | S1-S7 | inline | `test(delivery): verify release cancellation and concurrency`

## Log

L1 — 2026-11-05 — Original request (verbatim):

> ahora vamos a hacer un cambio:
> Título
> El repartidor no puede soltar un pedido que tomó
> Descripción
> No hay endpoint para liberar ni reasignar un pedido (el claim solo toma pedidos sin asignar). Si el repartidor no puede ir, el pedido queda asignado y nadie más lo puede tomar. Definir además un procedimiento autorizado para pedidos ya retirados, como FD-65, con motivo y trazabilidad.

L2 — 2026-11-05 — Scope decision: "Solo API y backend".

L3 — 2026-11-05 — Authorization decision: "Repartidor y Soporte". The Support actor requires a new canonical `Soporte` role because the current domain recognizes only `Cliente`, `Restaurante`, and `Repartidor`.

L4 — 2026-11-05 — Post-pickup outcome decision: "CANCELADO o RECHAZADO a elección". No reassignment is permitted after pickup.

L5 — 2026-11-05 — Reason decision: "Motivo obligatorio siempre" for both release and post-pickup cancellation.

L6 — 2026-11-05 — Architecture decision: "Orquestar desde Orders (recomendado)". Public endpoints and durable audit live in `orders-service`; `delivery-service` is synchronized through `/api/internal/*` with `X-Internal-Api-Key`.

L7 — 2026-11-05 — Approved public contract: `POST /api/orders/{orderId}/release` with `{ "reason": "..." }`; `POST /api/orders/{orderId}/cancel-after-pickup` with `{ "status": "Cancelado|Rechazado", "reason": "..." }`; `GET /api/orders/{orderId}/delivery-events` restricted to `Soporte`.

L8 — 2026-11-05 — Approved consistency model: durable `PENDING|SUCCEEDED|FAILED` operation in `orders-service`; idempotent internal Delivery calls; conditional finalization; recoverable pending operations. Orders and Delivery never access each other's database.

L9 — 2026-11-05 — Approved transition rules: release is allowed only for `LISTO_PARA_RETIRO` and the owning courier; post-pickup cancellation is allowed only for `RETIRADO` or legacy `EN_CAMINO`, by the owning courier or `Soporte`, ending in `CANCELADO` or `RECHAZADO`.

L10 — 2026-11-05 — Risk: item 2 (security) because a new privileged role and ownership checks are introduced; item 4 (concurrency) because claim, release, and state transitions must remain coherent under races. Independent verification is required after implementation.

L11 — 2026-11-05 — T1 completed in commit `2a6b9e3` (`docs(design): document delivery release and cancellation`). The approved design is `docs/plans/2026-11-05-delivery-release-cancellation-design.md`.

L12 — 2026-11-05 — Implementation plan created at `docs/plans/2026-11-05-delivery-release-cancellation-implementation.md` with WU-1 through WU-7, focused checks, differential checks, and review-workload boundaries.

L13 — 2026-11-05 — WU-1 completed in commit `2fe5c35` (`feat(auth): add support role`). Verification: Auth unit suite passed; `AuthPostgresIT` was skipped because Docker/Testcontainers was unavailable. The migration is additive (`V4__add_support_role.sql`) and the merged seed was not edited.

L14 — 2026-11-05 — WU-2 RED observed: focused `compileTestJava` failed with the expected missing release/close symbols (exit 1). GREEN observed after implementation: focused WU-2 tests passed (exit 0), then the complete Delivery unit suite (`*Test`) passed with `BUILD SUCCESSFUL` in 37 s.

L15 — 2026-11-05 — WU-2 completed in two reviewable commits: `6038b62` (`feat(delivery): add conditional route release and cancellation`) and `6bc46fc` (`feat(delivery): expose internal route release endpoint`). The split keeps each authored slice below the 400-line review budget while preserving tests with behavior.

L16 — 2026-11-05 — Delivery Testcontainers verification was unavailable because no Docker environment was detected (`Could not find a valid Docker environment`). The four failing classes were `DeliveryServiceApplicationIT`, `DeliveryMigrationIT`, `JpaDeliveryPersonRepositoryIT`, and `JpaRouteRepositoryIT`; the failure is environmental and unrelated to the changed unit behavior. No retry was attempted after the root cause was confirmed.
