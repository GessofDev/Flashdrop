# Implementation plan: delivery release and post-pickup cancellation

This plan executes the approved design in `docs/plans/2026-11-05-delivery-release-cancellation-design.md`. The canonical feature specification is `odd/tasks/delivery-release-cancellation.md`; every worker must read that file through `## Log` before editing and report the covered `S#` values.

## Work-unit sequence

| WU | Depends on | Scope | Expected commit |
|---|---|---|---|
| WU-1 | none | Add canonical `Soporte` role in `auth-service`. | `feat(auth): add support role` |
| WU-2 | none | Add idempotent route release and cancellation states in `delivery-service`. | `feat(delivery): expose idempotent route release and cancellation` |
| WU-3 | WU-2 contract | Add durable delivery-operation model and persistence in `orders-service`. | `feat(orders): add durable delivery operations` |
| WU-4 | WU-2, WU-3 | Add pre-pickup release endpoint and saga in `orders-service`. | `feat(orders): release assigned orders before pickup` |
| WU-5 | WU-2, WU-3 | Add post-pickup cancellation endpoint and saga in `orders-service`. | `feat(orders): cancel picked-up orders with authorized support` |
| WU-6 | WU-3 | Add operation history, recovery, and API documentation. | `feat(orders): expose delivery operation history` |
| WU-7 | WU-1..WU-6 | Cross-service regression and race verification. | `test(delivery): verify release cancellation and concurrency` |

WU-1 and WU-2 are independent and may run concurrently because their allowed edit surfaces do not overlap. WU-3 through WU-6 are sequential because they share the Orders operation model and contracts.

## WU-1: canonical Support role

### Behavior

- `auth-service` seeds the canonical role name `Soporte` through a new Flyway migration; existing merged migrations remain unchanged.
- The role is assignable through the existing internal role-assignment flow and emitted in the JWT `roles[]` claim.
- No new public user-management endpoint is introduced.

### Tests

- Migration or repository test proves the role exists exactly once and is idempotent under Flyway.
- Role-assignment test proves `Soporte` can be assigned to a user.
- JWT test proves `Soporte` appears in `roles[]` without changing the roles of existing users.

### Focused checks

```bash
cd services/auth-service && ./gradlew test
```

## WU-2: Delivery internal operations

### Behavior

- Add `CANCELADO` and `RECHAZADO` to `RouteStatus` using the existing human-readable database values.
- Add a locked, conditional route-release repository operation that clears `delivery_person_id` and sets `PENDIENTE` only for the expected courier.
- Add `POST /api/internal/routes/order/{orderId}/release`, protected by the existing internal API key.
- Extend the existing internal route-status update to accept `Cancelado` and `Rechazado`.
- Repeated release and repeated same-target cancellation are successful no-ops. Conflicting assignment or terminal state returns `409`.

### Tests

- Domain value-object tests for the new route statuses.
- Repository test for expected courier, wrong courier, already released, and terminal route.
- Controller test for internal API-key behavior and request/response mapping.

### Focused checks

```bash
cd services/delivery-service && ./gradlew test
```

## WU-3: durable Orders operation model

### Behavior

- Add `order_delivery_operations` with the fields and states from the approved design.
- Keep the operation model independent of Spring/JPA in the domain layer.
- Add repository ports and JPA adapters for create, lock, mark succeeded, mark failed, list by order, and list pending.
- Store reason, actor, role, previous/resulting assignment and status, correlation id, failure code, and timestamps.
- Add `SOPORTE` to the Orders role enum without granting it unrelated order-status permissions.

### Tests

- Domain state tests for `PENDING -> SUCCEEDED|FAILED` and forbidden transitions.
- JPA tests prove operation creation, locking, state persistence, and chronological history.
- Migration test proves the table and indexes exist.

### Focused checks

```bash
cd services/orders-service && ./mvnw test
```

## WU-4: release before pickup

### Behavior

- Add `POST /api/orders/{orderId}/release` with `{ "reason": "..." }`.
- Validate JWT role `Repartidor`, current delivery ownership, `LISTO_PARA_RETIRO`, and mandatory reason.
- Create the durable operation before contacting Delivery.
- Call the idempotent Delivery release endpoint, then clear `delivery_id` and mark the operation succeeded in one Orders transaction.
- Keep the order in `LISTO_PARA_RETIRO`; a failed Delivery call leaves the order unchanged and the operation retryable.
- Repeat calls are safe and do not create conflicting assignments.

### Tests

- Public contract tests for validation and HTTP mapping.
- Use-case tests for owner, wrong courier, missing reason, wrong state, Delivery failure, retry, and idempotency.
- Repository race test for claim versus release.

### Focused checks

```bash
cd services/orders-service && ./mvnw test -Dtest='*Release*'
```

## WU-5: cancel after pickup

### Behavior

- Add `POST /api/orders/{orderId}/cancel-after-pickup` with `{ "status": "Cancelado|Rechazado", "reason": "..." }`.
- Authorize the owning `Repartidor` or any `Soporte`; when both roles are present, Support authorization prevails.
- Accept only `RETIRADO` or legacy `EN_CAMINO` as the source state.
- Call Delivery to close the route with the same target state, then update the order and operation atomically.
- Repeat calls with the same target are successful; a different target is `409`.
- The generic order-status endpoint cannot invoke these transitions.

### Tests

- Authorization matrix for courier owner, courier non-owner, Support, both roles, and unrelated roles.
- State and target validation tests.
- Delivery failure, retry, idempotency, and audit-result tests.
- Regression test proving the generic status endpoint still rejects the reserved transitions.

### Focused checks

```bash
cd services/orders-service && ./mvnw test -Dtest='*CancelAfterPickup*'
```

## WU-6: history, recovery, and contracts

### Behavior

- Add `GET /api/orders/{orderId}/delivery-events`, restricted to `Soporte`.
- Return operation history in chronological order without exposing internal failure details beyond the approved fields.
- Add a scheduled recovery path for pending operations; recovery is idempotent and bounded.
- Update Orders OpenAPI and relevant repository documentation.
- Keep all actor identity fields sourced from the JWT.

### Tests

- Controller tests for Support access and forbidden access.
- Repository/use-case test for chronological history.
- Recovery test for pending operation completion and retry limits.
- OpenAPI contract check.

### Focused checks

```bash
cd services/orders-service && ./mvnw test
```

## WU-7: cross-service verification

### Required evidence

- Full `auth-service` test suite.
- Full `delivery-service` test suite.
- Full `orders-service` test suite.
- Focused race test proving claim and release have one winner.
- Focused retry test proving a partially applied operation converges.
- Contract inspection for the two public endpoints, internal release endpoint, and route-status extension.
- `git diff --check` and repository status inspection.

### Differential check

The implementation touches shared claim, role, repository, and route-status code. Compare the previous commit and the feature branch for these observable behaviors:

1. Existing claim still assigns only unclaimed orders.
2. Existing `LISTO_PARA_RETIRO -> RETIRADO` still succeeds for the owning courier.
3. Existing `RETIRADO -> ENTREGADO` and delivery-code verification remain unchanged.
4. Existing cancellation/rejection of unassigned orders keeps its previous response.

Any unrequested difference is a regression and must be fixed and pinned with a test.

## Review workload guard

- Keep each WU as one reviewable commit with its tests and documentation.
- Target approximately 400 authored changed lines per commit.
- Do not split tests away from behavior.
- If a cohesive WU exceeds the budget, split at the next behavior boundary rather than compressing code or deleting coverage.
- The final branch may contain multiple work-unit commits; no single PR should be assembled from the accumulated branch.

## Risk and verification

**Risk: item 2 (security)** because `Soporte` introduces privileged access and ownership checks guard all mutations.  
**Risk: item 4 (concurrency)** because claim, release, cancellation, and recovery may race.

After implementation, run `gentle_review` assessment over the diff and escalate both items for independent verification. The verifier must inspect authorization, rejected-input invariants, idempotency, and race behavior through public interfaces and isolated persistent state.
