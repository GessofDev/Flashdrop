# Spec: `store-flow`

> **Revisión aplicada** (Javier, 2026-09-24): ownership se resuelve localmente en catalog usando `GetRestaurantByUserIdUseCase` (no HTTP self-call). El rol real es `Restaurante` (no `store_owner`). Catalog debe agregar Spring Security con validación JWT RS256 contra JWKS de Auth. El gateway requiere código nuevo para soportar multipart. `products.image` es `varchar(255)`, no TEXT — se persiste object key, no URL firmada.

## 1. Overview

Este spec formaliza los requisitos para que el dueño de tienda gestione su catálogo de productos (CRUD con ownership derivada del JWT, upload de imágenes a S3/MinIO) y vea métricas de ventas agregadas. Toca `orders-service` (métricas), `catalog-service` (CRUD + upload + Spring Security) y `gateway` (rutas + multipart). **No hay migraciones Flyway** (asumiendo estrategia de object key para imágenes), ni nuevos servicios.

## 2. Functional Requirements

### FR-1 — Métricas de ventas por restaurante

`GET /api/orders/restaurants/{restaurantId}/sales-summary?range={day|week|month}` devuelve:

```json
{
  "restaurantId": 42,
  "range": "week",
  "from": "2026-09-15T00:00:00Z",
  "to": "2026-09-22T00:00:00Z",
  "totalOrders": 137,
  "totalRevenue": 845620,
  "averageTicket": 6172,
  "topProducts": [
    {"productId": "uuid", "productName": "...", "quantitySold": 88, "revenue": 245000}
  ]
}
```

- `range` default `week`. Valores válidos: `day`, `week`, `month`. 400 si valor inválido.
- Auth: JWT con rol `Restaurante`. 403 si rol incorrecto.
- Ownership: el `restaurantId` del path debe corresponder al restaurante del dueño autenticado. **403 si no coincide**. Validación: orders-service llama a `GET /api/internal/restaurants?userId={userId}` en catalog (con `X-Internal-Api-Key` header) y compara el resultado con el `restaurantId` del path. Sin cache (es un read a DB indexada, no necesita cache).
- Solo se cuentan órdenes en estado `ENTREGADO` (la tienda ve ventas cerradas, no pedidos pendientes).
- `topProducts` retorna hasta 5 productos ordenados por cantidad vendida DESC.
- `from`/`to` se calculan server-side según `range`:
  - `day`: últimas 24h
  - `week`: últimos 7 días
  - `month`: últimos 30 días

### FR-2 — Namespace owner `/api/catalog/my/products`

Todos los endpoints bajo `/api/catalog/my/*` requieren **JWT con rol `Restaurante`** validado por `SecurityConfig` en catalog (Spring Security + JWT RS256 contra JWKS de Auth).

El `restaurantId` **nunca** se acepta en el body ni en el query string. Se resuelve server-side:

```
userId (del JWT) → GetRestaurantByUserIdUseCase.execute(userId)
                  → RestaurantRepositoryPort.findByUserId(userId)
                  → Optional<Restaurant> → restaurantId (Long)
```

(Resolución **local**, sin HTTP self-call ni cache Caffeine. El puerto `RestaurantOwnershipResolver` se mantiene como interfaz HTTP solo para que **otros** servicios — orders — resuelvan.)

Si el `userId` no tiene restaurante asociado → 403 con mensaje claro.

#### FR-2.1 — Crear producto

`POST /api/catalog/my/products` con body:

```json
{
  "categoryId": 3,
  "name": "Hamburguesa doble",
  "description": "Carne, queso, lechuga, tomate",
  "price": 5500,
  "image": "products/2026/09/abc-uuid.webp",
  "available": true
}
```

- 201 Created con `ProductResponse` del producto creado.
- Validaciones: `name` no vacío, ≤ 100 chars; `price` > 0; `image` es un **object key** (string con formato `products/{yyyy}/{mm}/{uuid}.{ext}`), NO una URL; `categoryId` debe existir (FK).
- Sin `restaurantId` en el body (se ignora si viene, se deriva del JWT).

#### FR-2.2 — Listar productos del dueño

`GET /api/catalog/my/products` devuelve `[ProductResponse]` con **todos** los productos del restaurante del dueño autenticado (incluye `available=false` para permitir reactivar).

- Sin paginación para MVP (asumimos < 200 productos por tienda). Si el rendimiento lo pide, se agrega cursor-based en otro change.

#### FR-2.3 — Editar producto

`PUT /api/catalog/my/products/{productId}` con el mismo body que FR-2.1.

- 200 OK con `ProductResponse` actualizado.
- 404 si el producto no existe.
- 403 si el producto pertenece a otro restaurante (IDOR check contra el `restaurantId` resuelto del JWT).

#### FR-2.4 — Eliminar producto (soft delete)

`DELETE /api/catalog/my/products/{productId}`:

- Marca `available = false` en la fila. **No borra la fila.**
- 204 No Content.
- 404 si no existe.
- 403 si pertenece a otro restaurante.
- El catálogo público (FR-5) deja de mostrarlo inmediatamente.

### FR-3 — Upload de imagen a S3/MinIO

`POST /api/catalog/my/products/image` con `multipart/form-data`, campo `file`.

- MIME permitidos: `image/jpeg`, `image/png`, `image/webp`. 400 si otro tipo.
- Tamaño máximo: 5MB. 413 si excede (rechazo en el gateway o en Spring antes del controller).
- Auth: JWT con rol `Restaurante`.
- Genera un **object key estable**: `products/{yyyy}/{mm}/{uuid}.{ext}`.
- Sube al bucket configurado (`S3_BUCKET`).
- Devuelve 201 con `{objectKey, url}` — `objectKey` para persistir, `url` construida server-side (pública o firmada según config).
- 502 si el bucket no responde.

Variables de entorno nuevas en `infra/coolify/env.shared.template`:
- `S3_ENDPOINT` (default: Floci local endpoint)
- `S3_BUCKET` (default: `flashdrop-products`)
- `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION`
- `S3_PUBLIC_URL_BASE` (URL pública para construir la respuesta)

### FR-4 — Rutas del gateway

| Path | Upstream | Auth |
|---|---|---|
| `GET /api/orders/restaurants/{id}/sales-summary` | `orders-service:8083` | JWT (rol `Restaurante`) |
| `POST /api/catalog/my/products` | `catalog-service:8082` | JWT (rol `Restaurante`) |
| `GET /api/catalog/my/products` | `catalog-service:8082` | JWT (rol `Restaurante`) |
| `PUT /api/catalog/my/products/{id}` | `catalog-service:8082` | JWT (rol `Restaurante`) |
| `DELETE /api/catalog/my/products/{id}` | `catalog-service:8082` | JWT (rol `Restaurante`) |
| `POST /api/catalog/my/products/image` | `catalog-service:8082` | JWT (rol `Restaurante`) |

### FR-5 — Catálogo público filtrado por `is_available`

Los endpoints públicos de catalog (`GET /catalog/products`, `GET /catalog/products/{id}`, `GET /catalog/products?categoryId=...`, `GET /catalog/products?restaurantId=...`) deben devolver **solo productos con `is_available=true`**.

- `ListProductsUseCase.execute()` y `execute(categoryId, restaurantId)` deben filtrar por `isAvailable` cuando se invocan desde el controller público.
- El endpoint owner `/api/catalog/my/products` (FR-2.2) sigue devolviendo todos, activos e inactivos.

## 3. Non-Functional Requirements

### NFR-1 — Hexagonal purity

Cero imports de framework en `domain/` y `application/`. La interfaz `RestaurantOwnershipResolver` (que es para que **otros** servicios resuelvan vía HTTP) queda solo si Orders la necesita — ver FR-1.

### NFR-2 — Tests

- IDOR: tests que verifican que un dueño no puede editar/eliminar productos de otra tienda (403).
- Ownership local: tests que mockean `RestaurantRepositoryPort.findByUserId` y verifican que se invoca con el `userId` correcto.
- Métricas: tests con dataset controlado que validan los cálculos (count, sum, avg, top).
- Image upload: tests con stub de S3 (LocalStack o mock) + casos de MIME/tamaño.
- Catálogo público filtrado: tests que verifican que `available=false` no aparece en respuestas de `/catalog/products`.
- Spring Security: tests que verifican 401 sin JWT, 403 con JWT sin rol `Restaurante`, 200 con JWT válido.

### NFR-3 — Performance de métricas

P95 de `GET /api/orders/restaurants/{id}/sales-summary` ≤ 500ms con dataset de 10k órdenes por restaurante. Verificar con EXPLAIN en PR-orders-metrics; agregar índice si hace falta (`orders(restaurant_id, status, created_at)`).

### NFR-4 — Performance del catálogo público

P95 de `GET /catalog/products` sin cambios significativos respecto al estado actual (la query es la misma, solo agrega filtro `is_available=true`). El índice `products(is_available, restaurant_id)` puede ayudar si hay muchos productos inactivos.

### NFR-5 — Multipart en gateway

El gateway debe soportar `multipart/form-data` de hasta 6MB (5MB de imagen + overhead). Latencia adicional < 50ms. Si el body excede el límite, devolver 413 sin pasar al backend.

### NFR-6 — Seguridad JWT

- Catalog valida JWT RS256 contra el JWKS de Auth (`AUTH_JWKS_URI`). El `Authorization` header es reenviado por el gateway.
- Las rutas `/api/catalog/my/**` requieren rol `Restaurante` en los claims. Si el rol no está, 403.
- Si el JWT es inválido o expirado, 401.
- Las rutas públicas `/catalog/**` (lectura) siguen siendo `permitAll` (el gateway no las reescribe con claims).
- Las rutas internas `/api/internal/**` siguen cubiertas por `InternalApiKeyFilter` existente.

### NFR-7 — Contrato de errores

Catalog adopta `ApiError` de `shared-observability` (formato `{code, service, traceId, message}`) para consistencia con auth/orders/delivery. Si se mantiene `ErrorResponse` propio, documentar la divergencia en el README.

---

**Spec listo.** La subdivisión en PRs y tasks está en `tasks.md`.