# Spec: `store-flow`

## 1. Overview

Este spec formaliza los requisitos para que el dueño de tienda gestione su catálogo de productos (CRUD con ownership derivada del JWT, upload de imágenes a S3/MinIO) y vea métricas de ventas agregadas. Toca `orders-service` (métricas), `catalog-service` (CRUD + upload) y `gateway` (rutas). **No hay migraciones Flyway** ni nuevos servicios.

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
- Auth: JWT con rol `store_owner`. 403 si rol incorrecto.
- Ownership: el `restaurantId` del path debe corresponder al restaurante del dueño autenticado. **403 si no coincide** (validación contra el endpoint interno `GET /api/internal/restaurants?userId={userId}`).
- Solo se cuentan órdenes en estado `ENTREGADO` (la tienda ve ventas cerradas, no pedidos pendientes).
- `topProducts` retorna hasta 5 productos ordenados por cantidad vendida DESC.
- `from`/`to` se calculan server-side según `range`:
  - `day`: últimas 24h
  - `week`: últimos 7 días
  - `month`: últimos 30 días

### FR-2 — Namespace owner `/api/catalog/my/products`

Todos los endpoints bajo `/api/catalog/my/*` requieren JWT con rol `store_owner`.

El `restaurantId` **nunca** se acepta en el body ni en el query string. Se resuelve server-side vía `RestaurantOwnershipResolver`:

```
RestaurantOwnershipResolver.resolve(userId) → restaurantId
  └─ HTTP GET /api/internal/restaurants?userId={userId}
     (X-Internal-Api-Key header)
     cache TTL 60s en memoria
```

Si el `userId` no tiene restaurante asociado → 403 con mensaje claro.

#### FR-2.1 — Crear producto

`POST /api/catalog/my/products` con body:

```json
{
  "categoryId": 3,
  "name": "Hamburguesa doble",
  "description": "Carne, queso, lechuga, tomate",
  "price": 5500,
  "image": "https://cdn.example.com/products/abc.jpg",
  "available": true
}
```

- 201 Created con `ProductResponse` del producto creado.
- Validaciones: `name` no vacío, ≤ 100 chars; `price` > 0; `image` URL válida https; `categoryId` debe existir (FK).
- Sin `restaurantId` en el body (se ignora si viene).

#### FR-2.2 — Listar productos del dueño

`GET /api/catalog/my/products` devuelve `[ProductResponse]` con todos los productos del restaurante del dueño autenticado.

- Sin paginación para MVP (asumimos < 200 productos por tienda). Si el rendimiento lo pide, se agrega cursor-based en otro change.

#### FR-2.3 — Editar producto

`PUT /api/catalog/my/products/{productId}` con el mismo body que FR-2.1.

- 200 OK con `ProductResponse` actualizado.
- 404 si el producto no existe.
- 403 si el producto pertenece a otro restaurante (IDOR check).

#### FR-2.4 — Eliminar producto (soft delete)

`DELETE /api/catalog/my/products/{productId}`:

- Marca `available = false` en la fila. **No borra la fila.**
- 204 No Content.
- 404 si no existe.
- 403 si pertenece a otro restaurante.

### FR-3 — Upload de imagen a S3/MinIO

`POST /api/catalog/my/products/image` con `multipart/form-data`, campo `file`.

- MIME permitidos: `image/jpeg`, `image/png`, `image/webp`. 400 si otro tipo.
- Tamaño máximo: 5MB. 413 si excede.
- Auth: JWT con rol `store_owner`.
- Genera una key única: `products/{yyyy}/{mm}/{uuid}.{ext}`.
- Sube al bucket configurado (`S3_BUCKET`).
- Devuelve 201 con `{url}` (URL pública del bucket, o URL firmada TTL 7 días según config).
- 502 si el bucket no responde.

Variables de entorno nuevas en `infra/coolify/env.shared.template`:
- `S3_ENDPOINT` (default: Floci local endpoint)
- `S3_BUCKET` (default: `flashdrop-products`)
- `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION`
- `S3_PUBLIC_URL_BASE` (URL pública para construir la respuesta)

### FR-4 — Rutas del gateway

| Path | Upstream | Auth |
|---|---|---|
| `GET /api/orders/restaurants/{id}/sales-summary` | `orders-service:8083` | JWT (rol store_owner) |
| `POST /api/catalog/my/products` | `catalog-service:8082` | JWT (rol store_owner) |
| `GET /api/catalog/my/products` | `catalog-service:8082` | JWT (rol store_owner) |
| `PUT /api/catalog/my/products/{id}` | `catalog-service:8082` | JWT (rol store_owner) |
| `DELETE /api/catalog/my/products/{id}` | `catalog-service:8082` | JWT (rol store_owner) |
| `POST /api/catalog/my/products/image` | `catalog-service:8082` | JWT (rol store_owner) |

## 3. Non-Functional Requirements

### NFR-1 — Hexagonal purity

Cero imports de framework en `domain/` y `application/`. Aplica al `RestaurantOwnershipResolver` (la interfaz en application, la implementación HTTP en infrastructure).

### NFR-2 — Tests

- IDOR: tests que verifican que un dueño no puede editar/eliminar productos de otra tienda (403).
- Ownership caching: tests que verifican que el cache TTL se respeta (clock fake en tests).
- Métricas: tests con dataset controlado que validan los cálculos (count, sum, avg, top).
- Image upload: tests con stub de S3 (LocalStack o mock) + casos de MIME/tamaño.

### NFR-3 — Performance de métricas

P95 de `GET /api/orders/restaurants/{id}/sales-summary` ≤ 500ms con dataset de 10k órdenes por restaurante. Verificar con EXPLAIN en PR-orders-metrics; agregar índice si hace falta (`orders(restaurant_id, status, created_at)`).

### NFR-4 — Cache invalidation del ownership resolver

Si bien el cache TTL es 60s, agregar métrica Prometheus `catalog_ownership_cache_hits_total` y `catalog_ownership_cache_misses_total` para observabilidad. Si en el futuro se quiere invalidación inmediata cuando un admin cambia el dueño de un restaurante, se agrega endpoint admin (fuera de scope).

---

**Spec listo.** La subdivisión en PRs y tasks está en `tasks.md`.