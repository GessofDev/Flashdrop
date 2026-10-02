# Spec: `profile-and-delivery`

## 1. Overview

Este spec formaliza los requisitos para cerrar el flujo del repartidor (selección de pedidos disponibles, transiciones de estado autorizadas) y para que cualquier usuario autenticado pueda editar su propio perfil. Toca `auth-service`, `orders-service`, `delivery-service` y `gateway`. **No hay migraciones Flyway** ni nuevos servicios.

## 2. Functional Requirements

### FR-1 — Edición de perfil

`PUT /auth/profile` actualiza los campos `name`, `lastName`, `phone`, `photo` del usuario autenticado. **El path NO lleva prefijo `/api/`** — el gateway expone `auth-service` bajo el prefijo `/auth` (ver `gateway/docker/gateway.yaml` línea 65-67: `prefix: /auth`, `stripPrefix: false`). La identidad se toma SIEMPRE del JWT (sub), nunca del body. Devuelve 200 con el `UserProfile` actualizado.

> **Email NO es editable** vía este endpoint. La tabla `users.email` es UNIQUE y la columna `login.login` se inicializa con `email.value()` en el alta — cambiar email sin actualizar `login.login` deja al usuario sin poder entrar. La edición de email es un flujo separado con verificación, fuera del alcance de este change.

Validaciones:
- `name` y `lastName`: no vacíos, ≤ 100 caracteres.
- `phone`: opcional. Si está presente, formato E.164 o formato local validado por `Phone` value object existente.
- `photo`: opcional. Si está presente, URL válida (https). Tamaño máximo de URL: 2048 caracteres.
- Email y `rut` **no son editables** vía este endpoint.
- Devuelve 400 si el body tiene campos extra no permitidos (strict schema).

Si el cambio de `phone` produce colisión con otro usuario → 409 con `ApiError(code="PHONE_ALREADY_EXISTS", message="...")`.

### FR-2 — Listado de pedidos disponibles para repartidor

`GET /api/orders/available-for-delivery?restaurant_id={long}&limit={int}` devuelve hasta `limit` pedidos en estado `LISTO_PARA_RETIRO` para el `restaurant_id` dado, ordenados FIFO por `created_at` ASC.

- `restaurant_id` es requerido. 400 si falta.
- `limit` opcional. Default 5. Rango válido: 1–50. 400 si excede.
- Auth: JWT con rol `Repartidor` (claim `roles[]` emitido por auth-service). 403 si rol incorrecto.
- No se requiere que el repartidor esté "asignado" a la tienda — cualquier repartidor puede ver pedidos disponibles.
- La respuesta es la misma `OrderListResponse` que ya usa `GET /api/orders` (reutilizar DTO).

### FR-3 — Claim sin mutación de estado

`POST /api/delivery/claim` ya no modifica `Order.status`. El estado del pedido queda en `LISTO_PARA_RETIRO` (o el estado que tenía antes del claim). Solo se persiste `delivery_id` en `delivery_routes` (quedando en estado `ASSIGNED`).

- El use case `ClaimDeliveryOrdersUseCase` en `orders-service` se modifica para NO llamar a `orderRepository.claimOrders` con `EN_CAMINO` (solo asocia `deliveryId` preservando `LISTO_PARA_RETIRO`) ni llamar a `deliveryPort.updateRouteStatus` con `EN_CAMINO`.
- En `delivery-service`, `ClaimDeliveryOrdersUseCaseImpl` ya asigna la ruta en estado `ASSIGNED` y delega a `orders-service` sin mutar estados de orden (no requiere cambios de código).
- La transición `LISTO_PARA_RETIRO → EN_CAMINO` ya **no ocurre automáticamente** — el repartidor debe disparar `PUT /api/orders/{id}/status` con `RETIRADO` (ver FR-4).
- El flag `delivery.claim.delegate-to-orders.enabled` en `delivery-service` sigue funcionando igual: cuando está activo, llama a `internalOrdersClient.claimOrders` para que `orders-service` actualice `delivery_id`.

### FR-4 — Autorización por rol en cambio de estado

`PUT /api/orders/{id}/status` valida el rol del JWT contra una matriz de transiciones permitidas (usando los roles canónicos del sistema emitidos por auth-service):

| Rol canónico (`roles[]`) | Transiciones permitidas (estado actual → estado nuevo) |
|---|---|
| `Repartidor` | `LISTO_PARA_RETIRO → RETIRADO`, `RETIRADO → ENTREGADO` |
| `Restaurante` | `NUEVO_PEDIDO → PREPARANDO`, `PREPARANDO → LISTO_PARA_RETIRO` |
| `Cliente` (o `client`) | (ninguna por ahora) |
| `admin` / `Admin` | todas las válidas (escape hatch, no usado en MVP) |

Códigos de error:
- **403** si rol no puede ejecutar esa transición (mensaje claro).
- **403** si rol es `Restaurante` pero `order.restaurantId != ownershipPort.resolveRestaurantId(currentUserId)` (IDOR — el dueño de tienda A no puede cambiar el estado de pedidos de tienda B).
- **409** si la transición no es válida por el estado actual del pedido (mensaje claro).
- **400** si el body tiene un estado no reconocido.
- **404** si el pedido no existe.

Las reglas de transición válidas por estado se mantienen en `Order.validateStatusTransition(newStatus)`. El plan original decía que esa lógica ya existía, pero en `main @ afc8f0a` solo rechaza `ENTREGADO → *`. **PR-orders-status-authz agrega la matrix completa `from × to`** en `Order.validateStatusTransition()`:

- `NUEVO_PEDIDO → PREPARANDO` (válido)
- `NUEVO_PEDIDO → LISTO_PARA_RETIRO` (corto-circuito, válido si la tienda decide saltarse PREPARANDO)
- `PREPARANDO → LISTO_PARA_RETIRO` (válido)
- `LISTO_PARA_RETIRO → RETIRADO` (válido)
- `LISTO_PARA_RETIRO → EN_CAMINO` (legacy — permitido pero deprecated)
- `RETIRADO → ENTREGADO` (válido)
- `RETIRADO → EN_CAMINO` (legacy — permitido pero deprecated)
- `ENTREGADO → *` rechazado (estado terminal)
- Cualquier otra transición rechazada con 409 (`OrderDomainException`).

Las validaciones son independientes y el orden implementado en `UpdateOrderStatusUseCase` (PR `ef2e32a` = PR-orders-status-authz, en `feat/orders-jwt-roles`) es: **rol/policy (403) → ownership (403) → transición (409)**. El rol se valida primero porque indica qué ownership evaluar — un usuario multirol actúa como `Restaurante` o como `Repartidor` según el estado que está fijando. El ownership se evalúa solo si el rol correspondiente está presente en `grantingRoles`: `Restaurante` resuelve su restaurante contra `CatalogPort.findRestaurantIdByUserId`; `Repartidor` resuelve su asignación contra `DeliveryPort.findDeliveryIdByUserId`. La transición de estado se valida al final con `Order.validateStatusTransition()` (409 si la matriz from×to la rechaza).

**Estado `EN_CAMINO` (legacy).** La matriz from×to permite llegar a `EN_CAMINO` desde `LISTO_PARA_RETIRO` o `RETIRADO` (compatibilidad con órdenes que ya están en ese estado), pero `RoleTransitionPolicy` no autoriza a ningún rol actual (`Cliente`, `Restaurante`, `Repartidor`) a entrar a `EN_CAMINO`. En la práctica, el endpoint `PUT /api/orders/{id}/status` no puede transicionar hacia `EN_CAMINO`. Las órdenes legacy que ya están en `EN_CAMINO` pueden salir a `RETIRADO` o `ENTREGADO` (matriz + policy lo permiten, `Repartidor` las finaliza).

### FR-5 — Rutas del gateway

| Path | Upstream | Auth |
|---|---|---|
| `PUT /auth/profile` | `auth-service:8081` | JWT |
| `GET /api/orders/available-for-delivery` | `orders-service:8083` | JWT (rol `Repartidor`) |
| `PUT /api/orders/{id}/status` | `orders-service:8083` | JWT |

`POST /api/delivery/claim` sigue existiendo sin cambios de ruta.

## 3. Non-Functional Requirements

### NFR-1 — Hexagonal purity

Cero imports de `jakarta.persistence`, `org.springframework.data`, o `org.springframework.boot` en `domain/` o `application/`. Aplica a los nuevos use cases y DTOs.

### NFR-2 — Tests

Cubrir matriz 2D rol × transición para `PUT /api/orders/{id}/status`. Cada combinación: rol permitido vs estado válido → resultado esperado. Mínimo 8 casos en IT.

### NFR-3 — Compatibilidad hacia atrás en el claim

Coordinación explícita con el dev de Flutter antes del merge del PR-delivery. El comportamiento de `POST /delivery/claim` cambia: ya no muta el estado. Riesgo documentado en design §11 item 1.

### NFR-4 — Performance del listado de disponibles

P95 de `GET /api/orders/available-for-delivery` ≤ 200ms en CI con 1000 pedidos por restaurante. Verificar con EXPLAIN en PR-orders-available; agregar índice si hace falta.

---

**Spec listo.** La subdivisión en PRs y tasks está en `tasks.md`.