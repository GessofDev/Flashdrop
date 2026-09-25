# Design: `profile-and-delivery`

> Referencia completa: [`docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`](../../../docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md).
> Este archivo es un resumen del design general enfocado a este OpenSpec change.

## Technical Approach

Cuatro cambios cohesivos en cuatro servicios:

1. **`auth-service`**: agregar `PUT /auth/profile` siguiendo el patrón de `GET /auth/profile` (mismo controller, mismo handler de bearer token, mismo `ApiResponse` envelope).
2. **`delivery-service`**: sin cambios de código (la auditoría de código confirmó que el servicio nunca mutó estados de pedido; solo asigna el repartidor a la ruta como `ASSIGNED` y delega a orders).
3. **`orders-service`**: agregar `GET /api/orders/available-for-delivery`, eliminar mutación de estado a `EN_CAMINO` en `ClaimDeliveryOrdersUseCase` y endurecer `PUT /api/orders/{id}/status` con una matriz rol→transición.
4. **`gateway`**: registrar la nueva ruta pública.

**Cero migraciones Flyway.** **Cero servicios nuevos.**

## Architecture Overview

```
Flutter (cliente / repartidor / tienda)
        │
        ▼
   Fastify Gateway (:3000)
        │
   ┌────┼─────────────────┬─────────────────┬──────────────┐
   ▼    ▼                 ▼                 ▼              ▼
 auth  delivery        orders          catalog        (gateway config)
 :8081 :8084            :8083           :8082

 Tocado en este change:
   auth-service    : PUT /auth/profile
   delivery-service: sin cambios de código (flujo intacto)
   orders-service  : GET /api/orders/available-for-delivery
                     PUT /api/orders/{id}/status con authz por rol
                     ClaimDeliveryOrdersUseCase sin mutación a EN_CAMINO
   gateway         : 1 ruta nueva
```

## Architecture Decisions

### ADR-1 — Claim no muta el estado

**Decisión:** el `claim` solo persiste `deliveryId` en `delivery_routes`. El estado del pedido queda como está (`LISTO_PARA_RETIRO` típicamente).
**Alternativas consideradas:**
- (A) Mantener comportamiento actual: el claim pasa a `EN_CAMINO`. **Rechazado** — no refleja el flujo físico y rompe la matriz de roles.
- (B) Eliminar la mutación. **Elegido** — el repartidor debe transicionar manualmente al primer `RETIRADO` cuando recoge el pedido.
**Consecuencias (+):** matriz de roles coherente; Flutter controla cuándo "arranca" la entrega.
**Consecuencias (−):** rompe el comportamiento de la app Flutter existente (riesgo #1 documentado en design).
**Rollback:** revertir el PR; no afecta DB.

### ADR-2 — Matriz rol→transición como mapa inmutable en código

**Decisión:** la matriz vive como `Map<String, Map<OrderStatus, Set<OrderStatus>>>` en una clase de dominio (`RoleTransitionPolicy`). No se externaliza a configuración para evitar errores operacionales.
**Alternativas:**
- (A) Config en `application-*.yml`. **Rechazado** — si el operador cambia la matriz por error, la autorización queda inconsistente.
- (B) Enum por rol. **Rechazado** — menos flexible si en el futuro se agregan roles o estados.
- (C) Mapa inmutable en código. **Elegido** — type-safe, testeable, requiere PR para cambiar.
**Consecuencias (+):** type safety; cambios quedan en revisión de código.
**Consecuencias (−):** cambiar permisos requiere deploy.

### ADR-3 — Un solo endpoint `PUT /api/orders/{id}/status` con authz, no dos endpoints separados

**Decisión:** `PUT /api/orders/{id}/status` permanece; se le agrega authz por rol.
**Alternativas:**
- (A) Endpoint separado `/api/delivery/orders/{id}/status` para repartidor. **Rechazado** — duplica lógica, complica el cliente Flutter que tiene que elegir endpoint según rol.
- (B) Mismo endpoint con authz. **Elegido** — DRY, el cliente usa un solo path.
**Consecuencias (+):** una sola ruta que recordar; menos código.
**Consecuencias (−):** el cliente tiene que saber qué `status` puede setear según su rol (esto ya es natural).

---

Para más detalle (flujos, errores, riesgos, fuera de alcance) ver el design general en `docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`.