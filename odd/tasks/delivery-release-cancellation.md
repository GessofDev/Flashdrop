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
T3 | S5-S6 | worker | `feat(orders): add durable delivery operations` (`5c09bf3`, `1ba7676`)
T4 | S1,S6 | worker | `feat(orders): release assigned orders before pickup` (`c5155db`)
T5 | S4-S6 | worker | `feat(orders): cancel picked-up orders with authorized support` (`0a19937`)
T6 | S1,S5 | inline | `feat(delivery): add conditional route release and cancellation` (`6038b62`); `feat(delivery): expose internal route release endpoint` (`6bc46fc`)
T7 | S2,S6 | worker | `feat(orders): expose delivery operation history` (`14e9bed`)
T8 | S1-S7 | inline | `test(delivery): verify release cancellation and concurrency` (`b4d24ee`)
T9 | S1,S5 | inline | `fix(delivery): run route release and close in a transaction` (`7057889`)
T10 | S1 | inline | `fix(delivery): make route assignment transactional at the adapter` (`df9d7d5`)

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

L17 — 2026-11-05 — WU-3 RED observed: compilation failed on missing DeliveryOperation and persistence symbols. Implementation completed and verified GREEN: 19 focused tests passed and 314 unit tests in orders-service passed. Changes split into reviewable slices `5c09bf3` (`feat(orders): add delivery operation domain model`) and `1ba7676` (`feat(orders): persist durable delivery operations`).

L18 — 2026-11-05 — WU-4 (T4) completed in commit `c5155db` (`feat(orders): release assigned orders before pickup`). 3-phase saga implemented in `ReleaseAssignedOrderUseCase`, HTTP delivery adapter wired to `/api/internal/routes/order/{orderId}/release`, validation request DTO, and controller mapped to `POST /api/orders/{orderId}/release` with `Repartidor` check. All 10 focused unit tests passed.

L19 — 2026-11-05 — WU-5 (T5) completed in commit `0a19937` (`feat(orders): cancel picked-up orders with authorized support`). 3-phase saga implemented in `CancelPickedUpOrderUseCase`, HTTP delivery adapter wired to route close endpoint (`PATCH /api/internal/routes/order/{orderId}/status`), validation request DTO, and controller mapped to `POST /api/orders/{orderId}/cancel-after-pickup` with Repartidor/Soporte checks (Support prevailing). All 12 focused unit tests passed.

L20 — 2026-11-05 — WU-6 (T7) completed in commit `14e9bed` (`feat(orders): expose delivery operation history and background recovery`). Implemented `GetDeliveryEventsUseCase`, `OrderDeliveryEventsController` mapped to `GET /api/orders/{orderId}/delivery-events` with `Soporte` check, `DeliveryOperationResponse` DTO, and `PendingDeliveryOperationRecoveryService` scheduled background recovery. All 7 focused unit tests passed.

L21 — 2026-11-05 — WU-7 (T8) completed. Comprehensive cross-service verification: 343 unit tests passed in orders-service; auth-service test suite passed; delivery-service 215 unit tests passed. Differential checks verified that claim, listo_para_retiro, retirado, and delivery code verification behavior remain unchanged.

L22 — 2026-11-13 — QA reported three symptoms on this flow: the courier release answered 500, cancelling an already-picked-up order left the route open, and F18 (6 wrong delivery codes) answered 500 instead of locking the order. Root cause: `JpaRouteRepositoryAdapter.releaseDeliveryPerson` and `closeRoute` take a `PESSIMISTIC_WRITE` row lock but were not transactional, and their only caller (`InternalRoutesController`) opens no transaction either, so Hibernate raised `TransactionRequiredException` (wrapped as `InvalidDataAccessApiUsageException`) and the global handler mapped it to 500. The F18 symptom was a rollback rather than a lock failure: the delivery 500 propagated into the `@Transactional` `VerifyDeliveryCodeUseCase` and undid the cancellation, and `CancelPickedUpOrderUseCase` only marks the durable delivery operation failed on 4xx, so a 5xx left the operation pending and the route open. Fixed by adding `@Transactional` to both adapter methods so the read-lock → mutate → write sequence shares one transaction and the lock is held until commit (T9, commit `7057889`).

L23 — 2026-11-13 — T9 verification: RED observed with the two annotations removed — the three new `JpaRouteRepositoryIT` tests failed (two with `TransactionRequiredException`, the concurrency one with both closes failing). GREEN observed after restoring them — the complete delivery-service suite passed 249 tests with 0 failures and 0 skips, with Docker available so all four Testcontainers ITs ran. Risk: item 4 (concurrency) — the `FOR UPDATE` lock now actually applies where the path previously never executed, so a concurrency guard was added (two simultaneous `closeRoute` calls with different terminal statuses: exactly one may commit, the other must be rejected with `RouteReleaseConflictException`). `assignDeliveryPerson` in the same adapter still relies on its caller's transaction (`ClaimDeliveryOrdersUseCaseImpl.execute`); it is safe today but remains a latent hazard for a future non-transactional caller.

L24 — 2026-11-13 — T10 (hardening of the latent hazard flagged in L23): `assignDeliveryPerson` received the same `@Transactional` treatment, so every lock-taking method of `JpaRouteRepositoryAdapter` now owns its transaction. The claim use case is `@Transactional`, so the annotation joins that transaction (`REQUIRED`) and the batch rollback semantics are unchanged; the method is now also safe when called on its own. Two more ITs pin it: one calls `assignDeliveryPerson` with no ambient transaction, the other races two claims of the same route and requires exactly one to win and the other to be rejected with `RouteAlreadyAssignedException` (which is what the D8 design expects the `FOR UPDATE` lock to guarantee). RED observed with the annotation removed (both new ITs failed with `TransactionRequiredException`), GREEN observed after restoring it — the complete delivery-service suite passed 251 tests with 0 failures and 0 skips. The same audit on orders-service found no equivalent hole: `JpaOrderRepositoryAdapter.findByIdForUpdate` and `JpaDeliveryOperationRepositoryAdapter.findByIdForUpdate` already carry `@Transactional`, and every caller (`VerifyDeliveryCodeUseCase`, `ReleaseAssignedOrderUseCase`, `CancelPickedUpOrderUseCase`) is transactional.
