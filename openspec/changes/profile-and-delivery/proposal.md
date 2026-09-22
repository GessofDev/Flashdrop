# Proposal: `profile-and-delivery`

## 1. Problem statement

El backend de FlashDrop ya soporta el flujo básico del cliente y de la tienda, pero faltan los endpoints y los ajustes necesarios para cerrar el flujo del **repartidor** (selección de tienda, listado de pedidos disponibles, transición de estados) y para que **cualquier usuario autenticado** (cliente, repartidor, tienda) pueda editar su propio perfil.

Concretamente:

- El repartidor **no puede ver qué pedidos están listos** para retirar de una tienda concreta — el endpoint actual `POST /delivery/claim` solo acepta IDs ya conocidos.
- El `POST /delivery/claim` muta el estado del pedido a `EN_CAMINO`, saltándose `RETIRADO`. Esto no refleja el flujo físico real (la tienda necesita saber que el pedido sigue siendo suyo hasta que el repartidor lo retire).
- `PUT /api/orders/{id}/status` no tiene **autorización por rol** — cualquier usuario autenticado puede transicionar a cualquier estado.
- `GET /auth/profile` existe pero no hay `PUT /auth/profile`. Ningún actor puede editar sus datos personales desde la app.

Este change cierra esos cuatro huecos. El diseño completo está en `docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`.

## 2. Target users / situations

**Usuarios internos:** apps Flutter (cliente, repartidor, tienda) consumiendo vía Gateway. No se agregan pantallas, este change solo habilita los endpoints que las pantallas necesitan.

**Casos cubiertos:**

- Repartidor abre la app → elige tienda → ve hasta 5 pedidos en `LISTO_PARA_RETIRO` → elige uno → `POST /delivery/claim` (sin cambio de estado).
- Repartidor llega a la tienda → `PUT /api/orders/{id}/status` con `RETIRADO`.
- Repartidor entrega al cliente → `PUT /api/orders/{id}/status` con `ENTREGADO`.
- Cualquier usuario autenticado edita su perfil desde la app → `PUT /auth/profile`.

## 3. Business rules / constraints

| Regla | Fuente |
|---|---|
| Cada microservicio dueño de su DB | `README.md` arquitectura |
| Llamadas internas llevan `X-Internal-Api-Key` | `shared-observability` |
| Hexagonal — sin Spring/JPA en `domain/` | Convención existente |
| TDD estricto donde esté disponible | `sdd-init/flashdrop_backend` |
| Conventional commits con scope, sin AI-attribution | `AGENTS.md` |
| Sin migración Flyway — el modelo existente soporta el cambio | Exploración §1.1 del design doc |

## 4. Product outcome / acceptance

- `PUT /auth/profile` actualiza `name/lastName/phone/photo` del usuario autenticado y devuelve el perfil nuevo.
- `GET /api/orders/available-for-delivery?restaurant_id=X&limit=5` devuelve hasta N pedidos en `LISTO_PARA_RETIRO` para ese restaurante.
- `POST /delivery/claim` ya **no** modifica el estado del pedido. Solo persiste `deliveryId` en `delivery_routes`.
- `PUT /api/orders/{id}/status` valida que el rol del JWT pueda ejecutar la transición solicitada. Devuelve 403 si no, 409 si la transición es inválida por estado actual.
- Los 19 tests unitarios existentes siguen verdes; se agregan los nuevos listados en `tasks.md` por PR.

## 5. Current-state gap (evidencia concreta)

| Gap | Evidencia |
|---|---|
| `POST /delivery/claim` muta `Order.status` a `EN_CAMINO` | `ClaimDeliveryOrdersUseCaseImpl.java` línea 80–90 (búsqueda de `assignDelivery`) |
| `Order.assignDelivery()` cambia status sin condicional | `Order.java` método `assignDelivery` |
| No existe `PUT /auth/profile` | `AuthController.java` solo tiene `@GetMapping("/profile")` |
| No existe endpoint de pedidos disponibles por restaurante | `OrderController.java` solo expone `listOrders`, `getOrderDetail` |
| `PUT /api/orders/{id}/status` no verifica rol | `UpdateOrderStatusUseCase.java` sin parámetro de rol |
| `ListOrdersUseCase` filtra por `userId` (interpretado como dueño de tienda) | `ListOrdersUseCase.java` líneas 30–40 |

## 6. Scope (in)

1. `auth-service`: nuevo use case `UpdateUserProfileUseCase`, DTO `UpdateProfileRequest`, endpoint `PUT /auth/profile` en `AuthController`.
2. `orders-service`: nuevo use case `ListAvailableOrdersUseCase`, endpoint `GET /api/orders/available-for-delivery` con query params.
4. `orders-service`: matriz rol → transición-permitida inyectada en `UpdateOrderStatusUseCase`. Lanzar `AccessDeniedException` si rol no autorizado, `OrderDomainException` si transición inválida.
5. `delivery-service`: eliminar la mutación de status en `ClaimDeliveryOrdersUseCaseImpl`. Ajustar `Order.assignDelivery()` para persistir solo `deliveryId`.
6. `gateway`: agregar 1 ruta nueva (`/api/orders/available-for-delivery`). Validar que `/api/orders/{id}/status` y `/api/auth/profile` (PUT) ya están ruteados o agregarlos si hace falta.
7. Tests unitarios + integration tests listados en `tasks.md` por PR.

## 7. Non-goals

- Edición de `vehicle` desde la app (queda fuera; ver design §10 item 1).
- Métricas de ventas para tienda (parte del change `store-flow`).
- CRUD de productos para tienda (parte del change `store-flow`).
- Imagen de perfil subida vía upload (queda fuera; ver design §10 item 3).
- Carga de archivos en auth-service (se queda como URL string).
- Mapas / geocoding / ruta computada en backend (decisión D-1 — frontend).
- Soporte para dueño de múltiples restaurantes.
- Notificaciones push al cliente por cambio de estado.

## 8. Open decisions

Ninguna al cierre de este proposal. Las decisiones arquitectónicas relevantes están en `docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md` §2.

---

**Proposal listo para review.** La subdivisión en PRs está en `tasks.md`.