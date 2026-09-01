# FlashDrop — Team Internal API Contracts

This document defines the **internal HTTP contracts** that the micro-services of the
FlashDrop platform expose to each other on the `/api/internal/**` namespace.

These endpoints are **not** part of the public OpenAPI surface. They are consumed only
between services within the same trusted boundary and are authenticated with the
`X-Internal-Api-Key` header (see *Authentication*).

> Scope covered in this generation: the contracts consumed by **orders-service**
> (Catalog C-1, C-2, C-3 ; Auth C-4 ; Delivery C-5, C-6, C-7) plus the planned
> **internal-orders** contract (C-8) exposed *by* orders-service.

---

## 1. Cross-cutting conventions

### 1.1 Identifier convention: domain UUID ↔ external Long

| Layer | Identifier type | Notes |
|-------|-----------------|-------|
| **Domain** (`cl.flashdrop...domain.model`) | `java.util.UUID` | Orders owns UUIDs internally. |
| **External services / Supabase** | `Long` | All external services expose IDs as `Long`. |

Conversion happens **only** in infrastructure adapters, via `IdConverter`:

```java
// infastructure/adapter/outbound/IdConverter.java
long   external = IdConverter.toLong(uuid);        // UUID → Long
UUID   domain   = IdConverter.toUuid(longValue);    // Long → UUID
```

Rules:
- Adapters always translate to/from `UUID` before crossing the domain boundary.
- No domain object, use case or port references a `Long` ID.
- When a service returns an entity, its `id` field is a `Long` in the JSON; the
  consuming adapter converts it to `UUID` with `IdConverter.toUuid(...)`.

### 1.2 Authentication

All `/api/internal/**` endpoints require the header:

```
X-Internal-Api-Key: <INTERNAL_API_KEY>
```

`INTERNAL_API_KEY` is configured per environment (`application.properties` / env var).
A shared API-gateway or service mesh **may** also enforce this; services must still
validate it on every internal call.

### 1.3 Error handling

| Situation | HTTP | Response |
|-----------|------|----------|
| Resource not found | `404` | `{"error":"Not Found","message":"..."}` |
| Upstream service down / timeout | `502` | `{"error":"Bad Gateway","message":"<svc> no disponible"}` |
| Validation | `400` | `{"error":"Bad Request","message":"..."}` |

orders-service wraps these as `ExternalServiceException` (carrying the upstream
`HttpStatus`) and maps them to the same `ErrorResponse` envelope via
`GlobalExceptionHandler`.

### 1.4 JSON envelope

Internal collection endpoints return a JSON array directly (`[ ... ]`).
Single-resource endpoints return the resource object directly (`{ ... }`).

---

## 2. Catalog contracts (consumed by orders-service)

Owner: **Javier** — `catalog-service`.

`RestClient` bean: `catalogInternalRestClient` (base URL `catalog.service.url`).

### C-1 — `GET /api/internal/products`

Bulk lookup of product prices/stock from the catalog. Orders never trusts
client-supplied prices; it always resolves them here.

**Request**
```
GET /api/internal/products?ids=101,102,103
X-Internal-Api-Key: <key>
Accept: application/json
```

**Response `200`** — array of `InternalProductDto`
```json
[
  {
    "id": 101,
    "restaurantId": 7,
    "name": "Burger",
    "description": "...",
    "image": "https://...",
    "price": 12500,
    "available": true
  }
]
```
| Field | Type | Description |
|-------|------|-------------|
| id | Long | External product id (→ `UUID` domain) |
| restaurantId | Long | Owning restaurant (→ `UUID` domain) |
| name | string | Product name |
| description | string | Product description |
| image | string | Image URL |
| price | number | Current unit price (CLP) |
| available | boolean | Whether it can be ordered |

`products` whose id is not found are omitted from the response.

**Consumer:** `CatalogHttpClientAdapter.findProductsByIds` → `CatalogPort`.

### C-2 — `GET /api/internal/restaurants/{id}`

Single restaurant lookup by id.

**Request**
```
GET /api/internal/restaurants/7
```
**Response `200`** — `InternalRestaurantDto`
```json
{ "id": 7, "name": "Burgers House", "address": "Los Leones 300", "userId": 42 }
```
| Field | Type | Description |
|-------|------|-------------|
| id | Long | Restaurant id (→ `UUID`) |
| name | string | Restaurant name |
| address | string | Pickup address |
| userId | Long | Owner user id (used by C-3) |

`404` if the restaurant does not exist.

**Consumer:** `CatalogHttpClientAdapter.findRestaurantById` → `CatalogPort`.

### C-3 — `GET /api/internal/restaurants?userId={userId}`

Resolves the restaurant owned by a user (owner scoping for listing/filtering).

**Request**
```
GET /api/internal/restaurants?userId=42
X-Internal-Api-Key: <key>
```
**Response `200`** — array of `InternalRestaurantDto` (usually one).
`[]` / `404` when the user has no restaurant.

**Consumer:** `CatalogHttpClientAdapter.findRestaurantIdByUserId` → `CatalogPort`.

### 3.1 Owned tables (orders-service)

orders-service reads its **own** `orders` and `order_items` tables through the
`SupabaseRestOrderRepositoryAdapter` (PostgREST on the Orders schema). This direct
REST access is permitted because the tables belong to the Orders service boundary.

The external `products` and `restaurant` tables are **not** accessed directly from
Orders anymore (migrated to C-1 / C-2 / C-3).

---

## 3. Auth contracts (consumed by orders-service)

Owner: **Nicolás** — `auth-service`.

`RestClient` bean: `authInternalRestClient` (base URL `auth.service.url`).

### C-4 — `GET /api/internal/users/{id}`

Returns basic profile data for a user. Replaces direct Supabase access to the
`users` table.

**Request**
```
GET /api/internal/users/42
X-Internal-Api-Key: <key>
```
**Response `200`** — `InternalUserDto`
```json
{ "id": 42, "fullName": "María Pérez", "email": "maria@x.com", "phone": "+56912345678" }
```
| Field | Type | Description |
|-------|------|-------------|
| id | Long | User id in Auth (→ `UUID` domain) |
| fullName | string | Full display name |
| email | string | Email |
| phone | string | Phone number |

`404` if the user does not exist.

**Consumers:**
- `AuthHttpClientAdapter.findUserById` → `UserPort`
- Enriches client profiles in `SupabaseRestClientAdapter.findClientById`
  (client name/email/phone come from Auth, not from a direct `users` query).

---

## 4. Delivery contracts (consumed by orders-service)

Owner: **Sebastián** — `delivery-service`.

`RestClient` bean: `deliveryInternalRestClient` (base URL `delivery.service.url`).

Orders no longer reads the `delivery` or `delivery_routes` tables directly; all
route lifecycle operations go through these endpoints.

### C-5 — `GET /api/internal/delivery/by-user/{userId}`

Resolves the delivery-person profile (if any) for a user.

**Request**
```
GET /api/internal/delivery/by-user/42
X-Internal-Api-Key: <key>
```
**Response `200`** — `InternalDeliveryPersonDto`
```json
{ "id": 9, "fullName": "Carlos B.", "phone": "+56999999999" }
```
| Field | Type | Description |
|-------|------|-------------|
| id | Long | Delivery-person id (→ `UUID` domain) |
| fullName | string | Full name |
| phone | string | Phone |

`404` → the user is not a delivery person (orders-service treats it as “no profile”).

**Consumer:** `DeliveryHttpClientAdapter.findDeliveryIdByUserId` → `DeliveryPort`.

### C-6 — `POST /api/internal/delivery/routes`

Creates a delivery route for an order (called right after a new order is persisted).

**Request**
```
POST /api/internal/delivery/routes
X-Internal-Api-Key: <key>
Content-Type: application/json

{
  "orderId": 501,          // Long, orders-service order id
  "pickupAddress": "Burgers House, Los Leones 300",
  "deliveryAddress": "Av. Providencia 1200",
  "distanceKm": 3.2,
  "estimatedMinutes": 20,
  "status": "Pendiente"
}
```
**Response `201`** with the created route (body minimal / `204 No Content` accepted).

| Field | Type | Description |
|-------|------|-------------|
| orderId | Long | Order id (→ `UUID`) this route belongs to |
| pickupAddress | string | Collection address |
| deliveryAddress | string | Drop-off address |
| distanceKm | number | Distance in km |
| estimatedMinutes | int | ETA in minutes |
| status | string | Initial route status |

**Consumer:** `DeliveryHttpClientAdapter.saveRoute` → `DeliveryPort` (called by
`CreateOrderUseCase`).

### C-7 — `PATCH /api/internal/delivery/routes/order/{orderId}`  (single)

Syncs the route status of a single order when its order status changes.

**Request**
```
PATCH /api/internal/delivery/routes/order/501?status=En%20camino
X-Internal-Api-Key: <key>
```
**Response `204`**. `404` if no route exists for the order (ignored).

**Consumer:** `DeliveryHttpClientAdapter.updateRouteStatusByOrder` → `DeliveryPort`
(called by `UpdateOrderStatusUseCase`).

### C-7 — `PATCH /api/internal/delivery/routes`  (bulk)

Syncs the route status for a batch of orders claimed by a delivery person.

**Request**
```
PATCH /api/internal/delivery/routes?status=En%20camino
X-Internal-Api-Key: <key>
Content-Type: application/json

[501, 502, 503]          // list of Long order ids
```
**Response `204`**. Missing routes are ignored.

**Consumer:** `DeliveryHttpClientAdapter.updateRouteStatus` → `DeliveryPort`
(called by `ClaimDeliveryOrdersUseCase`).

### 4.1 Owned tables (orders-service)

orders-service keeps its own `client` table (owner/customer profiles) on the Orders
schema and accesses it through `SupabaseRestClientAdapter` (PostgREST). The table is
considered part of the Orders boundary, so direct REST access is allowed. Client
profile enrichment (name/email/phone) is still routed through Auth (C-4) via
`UserPort`, **not** through a direct `users` table read.

---

## 5. Internal-Orders contract (exposed by orders-service) — C-8

Owner: **Orders** — consumed by other services that need order data.

Exposes a read-only internal endpoint. Planned (not yet implemented by other services):

### C-8 — `GET /api/internal/orders`

Bulk order lookup by id.

**Request**
```
GET /api/internal/orders?ids=501,502
X-Internal-Api-Key: <key>
```
**Response `200`** — array of `InternalOrderDto` (id, status, clientId, restaurantId, total…).

`OrderRepositoryPort.findByIds` is the domain entry point that this endpoint will
back once the controller is implemented.

---

## 6. Consumers map (orders-service view)

| Domain port | Adapter | Sources |
|-------------|---------|---------|
| `CatalogPort` | `CatalogHttpClientAdapter` | C-1, C-2, C-3 |
| `UserPort` | `AuthHttpClientAdapter` | C-4 |
| `DeliveryPort` | `DeliveryHttpClientAdapter` | C-5, C-6, C-7 |
| `ClientPort` | `SupabaseRestClientAdapter` | Orders `client` table (own) + C-4 enrichment |
| `OrderRepositoryPort` | `SupabaseRestOrderRepositoryAdapter` | Orders `orders` / `order_items` tables (own) |

Direct Supabase table access from orders-service is now restricted to **orders**,
**order_items** and **client** (all owned by the Orders boundary). The legacy direct
queries to `products`, `restaurant`, `users`, `delivery` and `delivery_routes`
have been removed in favor of the HTTP contracts above.

---

## 7. Configuration reference

| Property | Env var | Default | Purpose |
|----------|---------|---------|---------|
| `catalog.service.url` | `CATALOG_SERVICE_URL` | `http://localhost:8082` | Catalog base URL |
| `delivery.service.url` | `DELIVERY_SERVICE_URL` | `http://localhost:8084` | Delivery base URL |
| `auth.service.url` | `AUTH_SERVICE_URL` | `http://localhost:8081` | Auth base URL |
| `internal.api.key` | `INTERNAL_API_KEY` | `dev-key` | `X-Internal-Api-Key` value |
| `supabase.url` | `SUPABASE_URL` | — | PostgREST base (own tables) |
| `supabase.service-role-key` | `SUPABASE_SERVICE_ROLE_KEY` | — | PostgREST auth |

`X-Internal-Api-Key` is injected on every internal `RestClient` bean via
`InternalServiceClientConfig`.

---

## 8. Backward compatibility & rollout notes

- Until the producer services expose the `/api/internal/**` endpoints, orders-service
  behavior is unchanged in semantics (it will surface `502`/`404` exactly as the old
  shared-table reads did), so callers are not broken by the migration.
- The `client` table stays local to Orders; only the `users` dependency moved to Auth (C-4),
  preserving the existing enrichment behavior.
- Delivery routes are now created/synced through Delivery Service (C-6/C-7); orders-service
  no longer writes `delivery_routes` directly.

---

## 9. Gaps conocidos (deuda técnica)

### GAP-01 — ✅ RESUELTO (2026-09-01) — C-7 no soporta actualizar el estado de ruta por `orderId`

**Problema**

El contrato C-7 documentado en la sección 4 de este archivo (`PATCH
/api/internal/delivery/routes/order/{orderId}` single y `PATCH
/api/internal/delivery/routes` bulk) es el contrato contra el que Orders
está implementado: Orders necesita actualizar el estado de una ruta a
partir del `orderId` que ya tiene disponible (durante `ClaimDeliveryOrdersUseCase`
y `UpdateOrderStatusUseCase`), no a partir del `routeId` interno de Delivery,
que Orders nunca conoce.

**Comportamiento actual**

delivery-service (`InternalRoutesController`) solo expone:

```
PATCH /api/internal/routes/{routeId}/status
```

Es decir, por `routeId`, uno a la vez. No existe ningún endpoint que acepte
`orderId` como identificador, ni una variante bulk.

**Impacto**

`DeliveryHttpClientAdapter.updateRouteStatusByOrder` y
`DeliveryHttpClientAdapter.updateRouteStatus` (en orders-service) no pueden
llamar a ningún endpoint real de Delivery para sincronizar el estado de la
ruta. Ambos métodos degradan con gracia: registran un `WARN` con el/los
`orderId` y el `status` que no se pudo sincronizar, y continúan sin lanzar
excepción, para no tumbar la transacción de claim ni de cambio de estado.

Consecuencia concreta: después de un claim exitoso, el pedido en Orders
queda con `status = "En camino"` correctamente, pero la fila correspondiente
en `delivery_routes` (base de Delivery) se queda en su estado inicial
(`"Asignado"`/`ASSIGNED`) — el estado de la ruta no se sincroniza con el
estado real del pedido.

**Solución requerida a nivel de contrato/API**

delivery-service necesita exponer una forma de actualizar el estado de una
ruta a partir del `orderId`, por ejemplo (a definir con Sebastián, cualquiera
de las dos alcanza para destrabar ambos métodos de Orders):

- `PATCH /api/internal/routes/order/{orderId}/status` — single, análogo al
  C-7 documentado arriba pero resolviendo la ruta por `orderId` en vez de
  `routeId`.
- `PATCH /api/internal/routes/status` con body `{ "orderIds": [...], "status": "..." }`
  — bulk, para el caso de `ClaimDeliveryOrdersUseCase` que sincroniza varios
  pedidos reclamados en una sola llamada.

Nota de alcance: `RouteRepository.findByOrderId(Long orderId)` **ya existe**
en la capa de persistencia de delivery-service (confirmado en el código al
2026-09-01). El trabajo pendiente es exponer el/los endpoint(s) de arriba en
`InternalRoutesController` reutilizando ese método — no hace falta tocar la
capa de repositorio/dominio.

**Responsable:** Sebastián / delivery-service.

**Estado del E2E:** el flujo E2E real (crear pedido → reclamar → consultar)
**sigue funcionando de punta a punta** con este gap presente — el claim no
falla y el pedido se actualiza correctamente en Orders. Lo único incompleto
es la sincronización del estado de la ruta en la base de Delivery, que
queda pendiente hasta que exista el endpoint de arriba.

**Actualización (2026-09-01, commit `358d154`):** este commit de Sebastián
en la misma rama corrigió un bug de arranque no relacionado (constructor
duplicado en `HttpOrderServiceClientAdapter`) — no tocaba este gap.

**Resolución (2026-09-01, commit `904464d`):** Sebastián agregó

```
PATCH /api/internal/routes/order/{orderId}/status
Body: { "status": "<RouteStatus>" }
```

en `InternalRoutesController`, reutilizando `RouteRepository.findByOrderId` +
`RouteRepository.updateStatus` (sin cambios de dominio/repositorio, como se
anticipaba). Devuelve `400` si no existe ruta para ese `orderId`.

**Verificado en caliente contra Postgres real (Floci)** el 2026-09-01, no
solo por lectura de código:
- `PATCH /api/internal/routes/order/13/status {"status":"EN_CAMINO"}` → `200`,
  y el cambio quedó persistido en `delivery_routes.status` en la base real.
- `PATCH /api/internal/routes/order/99999/status` (orderId inexistente) →
  `400` con `"No route found for orderId=99999"`, tal como especifica el commit.

**Detalle importante para GAP-01b:** el `status` que este endpoint acepta es
un **token de enum** (`PENDIENTE`, `ASSIGNED`, `RETIRAR_PEDIDO`, `EN_CAMINO`,
`ENTREGADO`, case-insensitive) — probé `"En camino"` (el texto que produce
`OrderStatus.getValue()` en Orders, con espacio y sin mayúsculas de enum) y
Delivery lo rechazó con `400`. El wire-up en Orders (GAP-01b, abajo) va a
necesitar mapear el valor de `OrderStatus` de Orders al token de enum que
espera Delivery, no reenviar `OrderStatus.getValue()` tal cual.

#### GAP-01b — ✅ RESUELTO (2026-09-01) — Seguimiento en Orders

`DeliveryHttpClientAdapter.updateRouteStatusByOrder` y `.updateRouteStatus`
(commit `f8a8eb8`, orders-service) ya llaman al endpoint real de Delivery en
vez de ser no-ops:

1. Llaman a `PATCH /api/internal/routes/order/{orderId}/status`.
2. Traducen el valor de `OrderStatus` de Orders al token de enum de
   `RouteStatus` que espera Delivery vía `ORDER_STATUS_TO_ROUTE_TOKEN`
   (`"Listo para retiro"→RETIRAR_PEDIDO`, `"Retirado"→EN_CAMINO`,
   `"En camino"→EN_CAMINO`, `"Entregado"→ENTREGADO`). `"Nuevo pedido"` y
   `"Preparando"` no tienen ruta equivalente todavía y se omiten sin llamar
   a Delivery.
3. `updateRouteStatus` (bulk, usado por `ClaimDeliveryOrdersUseCase`) itera
   el endpoint single una vez por `orderId`, ya que no hay variante bulk en
   Delivery.
4. Mantiene la degradación con gracia (log `WARN` + continúa) ante
   cualquier falla HTTP — no cambia el diseño ya establecido, solo agrega
   la llamada real detrás.

**Verificado en vivo contra Postgres real (Floci), no solo por test:**
- `UpdateOrderStatusUseCase` (single): pedido 13 `En camino`→`Entregado` en
  Orders sincronizó `delivery_routes.status` `Asignado`→`ENTREGADO` en la
  base de Delivery.
- `ClaimDeliveryOrdersUseCase` (bulk-loop): claim del pedido 14 sincronizó
  `delivery_routes.status` `Asignado`→`EN_CAMINO`.

De paso se corrigieron `MockDeliveryServer`, `DeliveryHttpClientAdapterTest`
y `OrdersE2ESimulatedTest`, que seguían apuntando a las URLs viejas de antes
del fix de contrato (commit `641cc3e`) y esperaban que estos métodos
lanzaran excepción — estaban rotos desde ese commit y no se habían corrido
en la suite completa hasta ahora. 61/61 tests de orders-service en verde.

**Responsable:** Felipe / orders-service.
