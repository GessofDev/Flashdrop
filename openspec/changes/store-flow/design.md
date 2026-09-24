# Design: `store-flow`

> Referencia completa: [`docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`](../../../docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md).
> Este archivo es un resumen del design general enfocado a este OpenSpec change.
>
> **Revisión aplicada** (Javier, 2026-09-24): se eliminó el HTTP self-call con cache Caffeine (catalog ya tiene `GetRestaurantByUserIdUseCase` local). Se agregó Spring Security con validación JWT RS256 contra JWKS de Auth. Se cambió el rol `store_owner` → `Restaurante` (nombre real del seed). Se agregó la necesidad de código multipart en el gateway. Se reconoció que `products.image` es `varchar(255)` (no TEXT) y se optó por persistir object key, no URL firmada.

## Technical Approach

Cuatro cambios cohesivos:

1. **`orders-service`**: agregar `GET /api/orders/restaurants/{id}/sales-summary` con agregaciones (count, revenue, avg ticket, top products) y validación de ownership vía HTTP a catalog.
2. **`catalog-service`**: agregar namespace `/api/catalog/my/products` (CRUD completo con ownership **local** vía `GetRestaurantByUserIdUseCase` existente) + `POST /api/catalog/my/products/image` (multipart → S3/MinIO con object key) + filtrar `is_available=true` en catálogo público + agregar Spring Security con JWT RS256.
3. **`gateway`** (PR-gateway-2 con código nuevo, no solo YAML): parser multipart, bodyLimit ≥ 6MB, passthrough raw stream para multipart, preservar `Content-Type` con boundary.
4. **Pre-work**: alinear Spring Boot del monorepo, agregar `catalog-service-ci.yml`, aprovisionar S3 en Floci (bucket, endpoint, vars, secretos, CORS, task definition).

**Cero migraciones Flyway** (asumiendo estrategia de object key para imágenes). **Cero servicios nuevos.** Spring Security se agrega a la dependencia existente de catalog.

## Architecture Overview

```
Flutter (dueño de tienda)
        │
        ▼
   Fastify Gateway (:3000)  ← multipart support en PR-gateway-2
        │
   ┌────┼─────────────────┬─────────────────┐
   ▼    ▼                 ▼                 ▼
 auth delivery          orders          catalog
 :8081 :8084            :8083           :8082

 Tocado en este change:
   orders-service:  GET /api/orders/restaurants/{id}/sales-summary
                    (ownership via HTTP call a catalog)
   catalog-service: POST /api/catalog/my/products/image
                    POST/GET/PUT/DELETE /api/catalog/my/products
                    Spring Security + JWT RS256 contra JWKS de Auth
                    Filtrar is_available=true en catálogo público
   gateway:         2 rutas nuevas + multipart support (código nuevo)

 Storage nuevo:
   S3/MinIO en Floci (catálogo de imágenes de producto) — pendiente aprovisionamiento
```

## Architecture Decisions

### ADR-1 — Métricas en `orders-service`, no en `catalog-service`

**Decisión:** el endpoint vive en `orders-service` y se expone bajo `/api/orders/restaurants/{id}/sales-summary`.
**Alternativas:**
- (A) En `catalog-service`, llamando internamente a `orders-service`. **Rechazado** — suma un hop y un adapter sin beneficio claro.
- (B) En `orders-service`. **Elegido** — el dato es de pedidos, respeta ownership, gateway solo rutea.
**Consecuencias (+):** path semánticamente honesto ("es un agregado sobre pedidos"); una sola fuente de verdad.
**Consecuencias (−):** el cliente Flutter consume un path `/orders/...` para un dato "de mi tienda". Mitigado con naming claro (`sales-summary`).

### ADR-2 — Namespace `/api/catalog/my/*` con ownership derivada del JWT (resolución local)

**Decisión:** nuevo namespace separado. El `restaurantId` nunca viaja en body ni query; se resuelve server-side usando `GetRestaurantByUserIdUseCase` existente (puerto local `RestaurantRepositoryPort.findByUserId`).
**Alternativas:**
- (A) Mantener `/catalog/products` con authz por rol. **Rechazado** — mezcla lectura pública con escritura autenticada.
- (B) Namespace separado + resolver HTTP self-call con cache Caffeine. **Rechazado** (revisión Javier) — `catalog-service` ya tiene el puerto local. HTTP self-call agregaría latencia, cache obsoleta, config de API key y un punto de falla circular.
- (C) Namespace separado + resolver local directo (`GetRestaurantByUserIdUseCase`). **Elegido** — refleja el modelo mental del cliente ("estoy en mi panel"); el dato está en la propia DB, no necesita HTTP.
**Consecuencias (+):** sin hop de red, sin cache, sin riesgo de inconsistencia por admin reasignando dueño.
**Consecuencias (−):** N/A (es lo que el código actual ya hace para `/api/internal/restaurants?userId=...`).

> Para `orders-service`, que NO tiene acceso local a la DB de restaurantes, el endpoint interno HTTP se mantiene (es lo que ya existe). Ver ADR-3.

### ADR-3 — Spring Security + JWT RS256 en `catalog-service` (NUEVO)

**Decisión:** catalog agrega `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`, valida JWT RS256 contra el JWKS de Auth (`AUTH_JWKS_URI`), y autoriza rol `Restaurante` para `/api/catalog/my/**`.
**Alternativas:**
- (A) Confiar en header `X-User-Id` inyectado por el gateway. **Rechazado** — el gateway ya hace JWT validation pero no garantiza que el header no sea falsificable si alguien llega al backend directamente (defense in depth).
- (B) Compartir el `InternalApiKeyFilter` para `/api/catalog/my/**`. **Rechazado** — `InternalApiKeyFilter` es para service-to-service, no para usuarios finales.
- (C) Spring Security + JWT RS256 contra JWKS de Auth. **Elegido** — validación criptográfica real; consistente con cómo cualquier backend stateless debería validar tokens.
**Consecuencias (+):** seguridad robusta, defense in depth, consistente con `delivery-service` y otros.
**Consecuencias (−):** se agrega una dependencia pesada (`spring-boot-starter-security`); el `SecurityConfig` debe configurar correctamente el orden de filtros (después de `CorrelationIdFilter`, antes de `InternalApiKeyFilter`).

### ADR-4 — Rol `Restaurante` (no `store_owner`)

**Decisión:** autorizar contra el rol real `Restaurante` que está en `V2__seed_development.sql`.
**Razón:** `store_owner` no existe en el seed ni en `RoleRepository`; usar ese nombre en el plan/openspec causaría 403 silencioso en producción.
**Acción:** mapear el claim `roles[]` del JWT a authorities de Spring Security (`ROLE_Restaurante`). Si el JWT no trae `roles[]`, falla cerrado con 403.

### ADR-5 — Persistir object key, no URL firmada, en `products.image`

**Decisión:** el `image` field guarda un **object key estable** (formato `products/{yyyy}/{mm}/{uuid}.{ext}`). La URL pública o firmada se construye al responder.
**Alternativas:**
- (A) Persistir URL firmada (TTL 7 días). **Rechazado** — expira, queda como referencia muerta.
- (B) Persistir URL pública completa. **Rechazado** — `products.image` es `varchar(255)`, una URL completa de S3 puede > 255 chars; cambiar a `TEXT` requiere migración.
- (C) Persistir object key + construir URL al responder. **Elegido** — object keys son cortos y estables; la URL se construye con `S3_PUBLIC_URL_BASE + objectKey` o con un SDK call.
**Consecuencias (+):** sin migración; URLs siempre frescas; cliente Flutter puede pedir URL firmada bajo demanda si necesita.
**Consecuencias (−):** el cliente debe guardar el object key, no la URL. Si cambia el `S3_PUBLIC_URL_BASE`, las URLs construidas cambian (aceptable).

### ADR-6 — Filtrar `is_available=true` en catálogo público

**Decisión:** los endpoints públicos (`GET /catalog/products`, etc.) solo devuelven productos con `is_available=true`. Los endpoints owner (`/api/catalog/my/*`) devuelven todos.
**Alternativas:**
- (A) No filtrar. **Rechazado** — producto desactivado sigue visible para clientes.
- (B) Filtrar solo en repository. **Rechazado** — el use case público debe ser explícito.
- (C) Métodos separados en `ProductRepositoryPort`: `findAll()`, `findAllAvailable()`, `findByCategoryIdAndAvailableTrue()`, etc. **Elegido** — el controller público llama a los métodos con filtro; el controller owner llama a los métodos sin filtro.
**Consecuencias (+):** soft delete funciona end-to-end; reactivación es trivial (PUT con `available=true`).
**Consecuencias (−):** mantener dos familias de queries en el repo (poco overhead, queries derivadas de Spring Data).

### ADR-7 — Multipart en gateway requiere código (no solo config)

**Decisión:** PR-gateway-2 incluye código nuevo: parser multipart (`@fastify/multipart` o equivalente), `bodyLimit` ≥ 6MB, passthrough raw stream para multipart, preservar `Content-Type` con boundary.
**Razón:** `gateway/src/proxy/engine.ts` actual serializa body con `JSON.stringify`, lo que destruye el boundary de multipart. Sin parser nativo, no hay forma de reenviar el body intacto.
**Consecuencias (+):** upload funciona end-to-end; cumple el requirement del FR-3.
**Consecuencias (−):** PR-gateway-2 deja de ser "solo YAML" y se vuelve más complejo; debe incluir test de integración con imagen real.

### ADR-8 — Soft delete (no hard delete) en productos (sin cambios)

**Decisión:** `DELETE /api/catalog/my/products/{id}` marca `available=false`. No borra la fila.
**Razón:** preservar integridad referencial con `order_items` históricos.
**Consecuencias (+):** sin migraciones; sin orphans; analítica histórica intacta.
**Consecuencias (−):** la tabla crece. Aceptable para MVP.

---

Para más detalle (flujos, errores, riesgos, fuera de alcance) ver el design general en `docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`.