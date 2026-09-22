# Proposal: `store-flow`

## 1. Problem statement

El backend de FlashDrop expone el catálogo de productos y los restaurantes, pero **no hay endpoints para que el dueño de tienda gestione su propio catálogo desde la app** ni para que vea **métricas de ventas**. Concretamente:

- El endpoint `POST /catalog/products` acepta `restaurantId` en el body, lo cual es un **IDOR clásico**: cualquier usuario autenticado puede crear/editar productos para cualquier tienda.
- No hay `PUT/DELETE /catalog/products/{id}`.
- No hay endpoint para listar productos **del restaurante del dueño autenticado**.
- No hay upload de imágenes. La columna `image` acepta URL string pero no hay endpoint que devuelva esa URL.
- No hay métricas para el dueño de tienda (cuánto vendió, ticket promedio, top productos, etc.).

Este change agrega todo eso con la decisión arquitectónica de que el **gateway solo rutea** y el **dato vive en su dueño**: métricas en `orders-service` (que tiene las órdenes), productos en `catalog-service` (que tiene el catálogo), upload vía S3/MinIO (que ya tenés disponible en Floci).

El diseño completo está en `docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`.

## 2. Target users / situations

**Usuarios internos:** app Flutter de la tienda consumiendo vía Gateway. No se agregan pantallas en este change, solo los endpoints que la pantalla de "Mi Tienda" necesita.

**Casos cubiertos:**

- Dueño de tienda abre la app → ve métricas (`/api/orders/restaurants/{id}/sales-summary`).
- Dueño de tienda sube una foto → `POST /api/catalog/my/products/image` → recibe URL.
- Dueño de tienda crea un producto → `POST /api/catalog/my/products` con la URL en `image`.
- Dueño de tienda edita un producto → `PUT /api/catalog/my/products/{id}`.
- Dueño de tienda desactiva un producto → `DELETE /api/catalog/my/products/{id}` (soft delete).

## 3. Business rules / constraints

| Regla | Fuente |
|---|---|
| Cada microservicio dueño de su DB | `README.md` |
| Llamadas internas llevan `X-Internal-Api-Key` | `shared-observability` |
| Hexagonal — sin Spring/JPA en `domain/` | Convención |
| TDD estricto donde esté disponible | `sdd-init/flashdrop_backend` |
| Conventional commits con scope, sin AI-attribution | `AGENTS.md` |
| Sin migración Flyway — `products.image` ya existe como TEXT | Exploración §1.1 |

## 4. Product outcome / acceptance

- `GET /api/orders/restaurants/{id}/sales-summary?range=day|week|month` devuelve métricas agregadas del restaurante del dueño autenticado. 403 si el JWT no corresponde al dueño del restaurante.
- `POST /api/catalog/my/products` crea un producto. El `restaurantId` se deriva del JWT, no se acepta en el body. 403 si el JWT no es de un store_owner.
- `GET /api/catalog/my/products` lista los productos del restaurante del dueño.
- `PUT /api/catalog/my/products/{id}` edita un producto. 403 si el producto no pertenece al restaurante del dueño.
- `DELETE /api/catalog/my/products/{id}` marca el producto como `available=false` (soft delete).
- `POST /api/catalog/my/products/image` recibe multipart, sube a S3/MinIO, devuelve `{url}`. MIME válido (jpeg/png/webp), tamaño ≤ 5MB.
- Tests unitarios + integration tests listados en `tasks.md` por PR.

## 5. Current-state gap (evidencia concreta)

| Gap | Evidencia |
|---|---|
| `POST /catalog/products` no valida ownership | `ProductController.java` no extrae `userId` del JWT |
| No hay namespace `/api/catalog/my/*` | Búsqueda en `catalog-service`: no existe `MyStore*Controller` |
| No hay upload de imágenes | No existe `S3ProductImageStorage` ni similar |
| No hay métricas de ventas | `orders-service` no expone ningún endpoint de agregación |
| `RestaurantController` solo lista, no expone por dueño | `RestaurantController.java` solo `listRestaurants()` |
| `catalog-service` no tiene cliente HTTP a sí mismo para resolver `userId → restaurantId` | No existe `RestaurantOwnershipResolver` |

## 6. Scope (in)

1. `orders-service`: nuevo use case `GetRestaurantSalesSummaryUseCase` con agregaciones (count, revenue, avgTicket, topProducts), endpoint `GET /api/orders/restaurants/{id}/sales-summary`.
2. `catalog-service`: configurar cliente S3/MinIO + variables de entorno; endpoint `POST /api/catalog/my/products/image`.
3. `catalog-service`: namespace `/api/catalog/my/products` (CRUD completo) con ownership derivada del JWT.
4. `catalog-service`: `RestaurantOwnershipResolver` que llama internamente a `GET /api/internal/restaurants?userId={userId}` con cache en memoria (TTL 60s).
5. `gateway`: agregar 2 rutas nuevas (`/api/catalog/my/*` → catalog, `/api/orders/restaurants/{id}/sales-summary` → orders).
6. Tests unitarios + integration tests listados en `tasks.md` por PR.

## 7. Non-goals

- Edición de perfil de tienda (parte del change `profile-and-delivery`).
- Endpoints de repartidor (parte del change `profile-and-delivery`).
- Notificaciones de pedido nuevo a la tienda.
- Exportación de métricas (CSV, PDF).
- Multi-restaurante por dueño.
- Galería / gestión masiva de imágenes.
- Versionado de producto (historial de cambios).
- Multi-idioma en los nombres de producto.

## 8. Open decisions

Ninguna al cierre de este proposal. Las decisiones arquitectónicas relevantes están en `docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md` §2.

---

**Proposal listo para review.** La subdivisión en PRs está en `tasks.md`.