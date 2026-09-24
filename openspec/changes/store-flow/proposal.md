# Proposal: `store-flow`

> **Revisión aplicada** (Javier, 2026-09-24, contra `main @ afc8f0a`): el plan original proponía HTTP self-call con cache Caffeine y asumía Spring Security + multipart en gateway que no existen. Esta versión reutiliza `GetRestaurantByUserIdUseCase` existente en catalog, agrega seguridad JWT real, y reconoce que `products.image` es `varchar(255)` (no TEXT) y que S3 no está aprovisionado en Floci.

## 1. Problem statement

El backend de FlashDrop expone el catálogo de productos y los restaurantes, pero **no hay endpoints para que el dueño de tienda gestione su propio catálogo desde la app** ni para que vea **métricas de ventas**. Concretamente:

- El endpoint `POST /catalog/products` acepta `restaurantId` en el body, lo cual es un **IDOR clásico**: cualquier usuario autenticado puede crear/editar productos para cualquier tienda.
- No hay `PUT/DELETE /catalog/products/{id}`.
- No hay endpoint para listar productos **del restaurante del dueño autenticado**.
- No hay upload de imágenes. La columna `image` acepta string (object key o URL) pero no hay endpoint que devuelva esa referencia.
- No hay métricas para el dueño de tienda (cuánto vendió, ticket promedio, top productos, etc.).
- El catálogo público **no filtra `is_available`** — productos desactivados siguen visibles.

Este change agrega todo eso aprovechando la base existente en catalog (puertos, use cases, repositorios JPA y adaptadores) y reutilizando lo que ya está.

El diseño completo está en `docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`.

## 2. Target users / situations

**Usuarios internos:** app Flutter de la tienda consumiendo vía Gateway. No se agregan pantallas en este change, solo los endpoints que la pantalla de "Mi Tienda" necesita.

**Casos cubiertos:**

- Dueño de tienda abre la app → ve métricas (`/api/orders/restaurants/{id}/sales-summary`).
- Dueño de tienda sube una foto → `POST /api/catalog/my/products/image` → recibe `{objectKey, url}`.
- Dueño de tienda crea un producto → `POST /api/catalog/my/products` con el `image` (object key) en el body.
- Dueño de tienda edita un producto → `PUT /api/catalog/my/products/{id}`.
- Dueño de tienda desactiva un producto → `DELETE /api/catalog/my/products/{id}` (soft delete). El catálogo público deja de mostrarlo.

## 3. Business rules / constraints

| Regla | Fuente |
|---|---|
| Cada microservicio dueño de su DB | `README.md` |
| Llamadas internas llevan `X-Internal-Api-Key` | `shared-observability` |
| Hexagonal — sin Spring/JPA en `domain/` | Convención |
| TDD estricto donde esté disponible | `sdd-init/flashdrop_backend` |
| Conventional commits con scope, sin AI-attribution | `AGENTS.md` |
| **Imagen persistida como object key estable** (p.ej. `products/{yyyy}/{mm}/{uuid}.webp`), no como URL firmada | Este change |
| Roles reales en auth-service: `Cliente`, `Restaurante`, `Repartidor` (no `store_owner`) | `V2__seed_development.sql` |
| **Catalog debe agregar Spring Security** (no lo tiene hoy) y validar JWT RS256 contra JWKS de Auth | Este change |

## 4. Product outcome / acceptance

- `GET /api/orders/restaurants/{id}/sales-summary?range=day|week|month` devuelve métricas agregadas del restaurante del dueño autenticado. 403 si el JWT no corresponde al dueño del restaurante.
- `POST /api/catalog/my/products` crea un producto. El `restaurantId` se deriva del JWT vía `GetRestaurantByUserIdUseCase` (puerto local), no se acepta en el body. 403 si el JWT no es de un rol `Restaurante`.
- `GET /api/catalog/my/products` lista los productos del restaurante del dueño (incluye `available=false` para permitir reactivar).
- `PUT /api/catalog/my/products/{id}` edita un producto. 403 si el producto pertenece a otro restaurante (IDOR check).
- `DELETE /api/catalog/my/products/{id}` marca el producto como `available=false` (soft delete).
- `POST /api/catalog/my/products/image` recibe multipart, sube a S3/MinIO, devuelve `{objectKey, url}`. MIME válido (jpeg/png/webp), tamaño ≤ 5MB. 413 si excede, 502 si S3 falla.
- El catálogo público (`GET /catalog/products`, `GET /catalog/categories`) **solo devuelve productos con `available=true`**.
- Tests unitarios + integration tests listados en `tasks.md` por PR.

## 5. Current-state gap (evidencia concreta)

| Gap | Evidencia |
|---|---|
| `POST /catalog/products` no valida ownership | `ProductController.java` no extrae `userId` del JWT |
| No hay namespace `/api/catalog/my/*` | Búsqueda en `catalog-service`: no existe `MyStore*Controller` |
| No hay upload de imágenes | No existe `S3ProductImageStorage` ni similar |
| No hay métricas de ventas | `orders-service` no expone ningún endpoint de agregación |
| `catalog-service` **NO tiene Spring Security** | `find .../catalog -iname "*Security*"` → vacío. `grep spring-boot-starter-security` → vacío |
| `products.image` es `varchar(255)`, no TEXT | `V1__create_schema.sql`: `image varchar(255)` |
| `ListProductsUseCase` no filtra `is_available` | `findAll()`/`findByCategoryId()`/`findByRestaurantId()` sin filtro en el use case |
| `RestExceptionHandler` no cubre 401, 403, 413, 502 | Solo IllegalArgument, MethodArgumentNotValid, ResourceNotFound, DataIntegrityViolation, HttpMessageNotReadable, Exception |
| **Build integrado del monorepo está roto** para catalog | `services/build.gradle.kts` Spring Boot 3.3.5 vs `services/catalog-service/build.gradle.kts` 3.5.16. `services/gradlew.bat :catalog-service:test` FAIL |
| **No hay workflow CI para catalog** | Solo existen `auth-service-ci.yml` y `orders-service-ci.yml` |
| S3 no aprovisionado en Floci para catalog | `infra/floci/INFRASTRUCTURE.md` marca S3 como `(not used)`. `env.shared.template` no tiene vars S3 |
| Gateway no soporta multipart de 5MB | `package.json` no tiene `@fastify/multipart`. `engine.ts` serializa body con `JSON.stringify`. Body limit por defecto < 5MB |
| **Roles del seed NO son `store_owner`** | `V2__seed_development.sql`: `Cliente`, `Restaurante`, `Repartidor` |
| `catalog-service` ya tiene `GetRestaurantByUserIdUseCase` y `RestaurantRepositoryPort.findByUserId` | Reutilizable, NO crear HTTP self-call |

## 6. Scope (in)

1. `orders-service`: nuevo use case `GetRestaurantSalesSummaryUseCase` con agregaciones (count, revenue, avgTicket, topProducts), endpoint `GET /api/orders/restaurants/{id}/sales-summary`. Ownership via HTTP call a catalog (orders NO tiene acceso local a restaurant DB).
2. `catalog-service`: agregar Spring Security (`spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`), validar JWT RS256 contra JWKS de Auth, autorizar rol `Restaurante` para `/api/catalog/my/**`.
3. `catalog-service`: namespace `/api/catalog/my/products` (CRUD completo) con ownership derivada del JWT usando `GetRestaurantByUserIdUseCase` existente (puerto local, **no HTTP self-call**).
4. `catalog-service`: endpoint `POST /api/catalog/my/products/image` con cliente S3/MinIO. Persistir object key estable, no URL firmada.
5. `catalog-service`: filtrar `is_available=true` en queries del catálogo público. Endpoints owner `/api/catalog/my/*` pueden ver todos.
6. `catalog-service`: agregar handlers en `RestExceptionHandler` para 401, 403, 413, 502. Decidir si adoptar `ApiError` de shared-observability o mantener `ErrorResponse`.
7. `gateway` (**PR-gateway-2 con código nuevo, no solo YAML**): parser multipart (`@fastify/multipart` o equivalente), bodyLimit ≥ 6MB, passthrough raw stream para multipart, preservar `Content-Type` con boundary. Test de integración con imagen real.
8. **Tarea previa al PR-gateway-2**: alinear versión Spring Boot del monorepo (raíz 3.3.5 vs catalog 3.5.16). Agregar `catalog-service-ci.yml` que ejecute tests autónomos.
9. **Tarea previa al PR-catalog-image**: aprovisionar S3 en Floci (bucket, endpoint accesible, credenciales, CORS, vars S3 en `env.shared.template`, task definition ECS).
10. Tests unitarios + integration tests listados en `tasks.md` por PR.

## 7. Non-goals

- Edición de perfil de tienda (parte del change `profile-and-delivery`).
- Endpoints de repartidor (parte del change `profile-and-delivery`).
- Notificaciones de pedido nuevo a la tienda.
- Exportación de métricas (CSV, PDF).
- Multi-restaurante por dueño.
- Galería / gestión masiva de imágenes.
- Versionado de producto (historial de cambios).
- Multi-idioma en los nombres de producto.
- Cache distribuido para resolver ownership (es un read a la propia DB, es rápido sin cache).
- Hard delete de productos (soft delete es el acuerdo).

## 8. Open decisions

**Cerradas en este proposal (no más abiertas):**

1. ~~¿HTTP self-call con cache Caffeine para ownership?~~ → NO. Usar `GetRestaurantByUserIdUseCase` local en catalog. Orders mantiene el HTTP call (no tiene DB local).
2. ~~¿URL firmada persistida en `products.image`?~~ → NO. Persistir object key, construir URL al responder.
3. ~~¿Rol `store_owner`?~~ → `Restaurante` (nombre real del seed).
4. ~~¿Catalog depende de shared-observability?~~ → Decidir durante implementación si adoptar `ApiError` (consistencia con auth/orders/delivery) o mantener `ErrorResponse` (mínimo cambio). Recomendación: `ApiError`.

---

**Proposal listo para review.** La subdivisión en PRs está en `tasks.md`.