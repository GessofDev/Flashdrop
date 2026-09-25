# Plan de diseño — Features de repartidor, tienda y cliente

**Fecha:** 2026-09-22
**Estado:** Validado en sesión de brainstorming. Pendiente de handoff a devs.
**Cambios derivados:** `openspec/changes/profile-and-delivery/` + `openspec/changes/store-flow/`.

---

## 1. Resumen ejecutivo

Se incorporán al backend las features pendientes para soportar el flujo completo de tres roles en la app Flutter: **repartidor** (toma de pedidos, cambios de estado, edición de perfil), **dueño de tienda** (métricas de ventas, carga de productos con imagen) y **cliente** (edición de perfil).

Las cuatro áreas de trabajo se mapean limpiamente sobre los servicios existentes. **No se crea ningún servicio nuevo.** El cambio se particiona en dos OpenSpec changes paralelos con subdivisión por servicio para maximizar paralelismo entre devs.

| Servicio | Rol en este plan |
|---|---|
| `auth-service` | Edición de perfil (`PUT /auth/profile`) |
| `delivery-service` | Sin cambios en código (auditoría confirmó que no muta status; su claim solo asigna la ruta como `ASSIGNED` y delega a orders) |
| `orders-service` | Listado de pedidos disponibles, autorización por rol en cambio de estado, claim sin mutación de estado a `EN_CAMINO`, métricas de ventas |
| `catalog-service` | CRUD de productos con ownership derivada del JWT, upload de imagen a S3/MinIO |
| `gateway` | 3 rutas nuevas (2 a orders, 1 a catalog); sin código, solo config |

**Cero migraciones Flyway.** El modelo de datos soporta todo lo necesario.

---

## 2. Decisiones arquitectónicas tomadas (recap del brainstorming)

| # | Decisión | Conclusión |
|---|---|---|
| D-1 | Mapa para repartidor | **Solo frontend.** El backend expone `pickupAddress` y `deliveryAddress`; Flutter usa Google Maps SDK con geocoding client-side |
| D-2 | Transiciones de estado del repartidor | **Dos:** `LISTO_PARA_RETIRO → RETIRADO` (pickup confirmado) y `RETIRADO → ENTREGADO` (entrega confirmada). El `claim` ya no cambia estado |
| D-3 | Endpoint de métricas de tienda | **En `orders-service`:** `GET /api/orders/restaurants/{id}/sales-summary?range=day|week|month`. Gateway solo rutea |
| D-4 | Endpoint de pedidos disponibles para repartidor | **En `orders-service`:** `GET /api/orders/available-for-delivery?restaurant_id&limit`. JWT-gated, mismo namespace que el resto del cliente |
| D-5 | Edición de perfil | **`PUT /auth/profile` para los tres roles.** Campos editables: `name`, `lastName`, `phone`, `photo`. `vehicle` no se edita desde la app (queda fijo o se cambia por admin) |
| D-6 | Ownership de productos | **Namespace separado:** `/api/catalog/my/products`. El `restaurantId` se deriva del JWT, no se acepta en el body |
| D-7 | Imágenes de producto | **Upload multipart al backend → S3/MinIO.** Endpoint `POST /api/catalog/my/products/image`. Floci replica el entorno AWS en dev y prod |

---

## 3. Contratos de API

### 3.1 Endpoints NUEVOS

| Path | Método | Servicio | Auth | Body | Respuesta |
|---|---|---|---|---|---|
| `/auth/profile` | `PUT` | auth-service | JWT | `{name, lastName, phone, photo}` (sin `email`, sin `rut`) | `UserProfile` |
| `/api/orders/available-for-delivery` | `GET` | orders-service | JWT (rol delivery) | — (query: `restaurant_id` req, `limit` opcional default 5) | `[OrderListResponse]` |
| `/api/orders/restaurants/{restaurantId}/sales-summary` | `GET` | orders-service | JWT (rol store_owner) | — (query: `range` opcional `day`/`week`/`month` default `week`) | `SalesSummaryResponse` |
| `/api/catalog/my/products` | `POST` | catalog-service | JWT (rol store_owner) | `{categoryId, name, description, price, image, available}` (sin `restaurantId`) | `ProductResponse` |
| `/api/catalog/my/products` | `GET` | catalog-service | JWT (rol store_owner) | — | `[ProductResponse]` |
| `/api/catalog/my/products/{productId}` | `PUT` | catalog-service | JWT (rol store_owner) | `{categoryId, name, description, price, image, available}` | `ProductResponse` |
| `/api/catalog/my/products/{productId}` | `DELETE` | catalog-service | JWT (rol store_owner) | — | 204 No Content |
| `/api/catalog/my/products/image` | `POST` | catalog-service | JWT (rol store_owner) | `multipart/form-data` campo `file` (jpeg/png/webp, ≤5MB) | `{url}` |

### 3.2 Endpoints MODIFICADOS (mismo path)

| Path | Cambio |
|---|---|
| `PUT /api/orders/{id}/status` | Autorización por rol + validación de transición. Matriz rol → transiciones permitidas: `delivery` solo puede transicionar a `RETIRADO` o `ENTREGADO`; `store_owner` puede transicionar `NUEVO_PEDIDO → PREPARANDO → LISTO_PARA_RETIRO`. 403 si rol no autorizado, 409 si transición inválida |
| `POST /delivery/claim` | El use case **deja de cambiar** `Order.status`. Solo persiste `deliveryId` en la ruta. El estado del pedido queda en `LISTO_PARA_RETIRO` hasta que el repartidor confirme el pickup con un PUT subsecuente |

### 3.3 Endpoints INTACTOS (verificados)

- `GET /catalog/restaurants` — el repartidor lo usa para elegir tienda.
- `GET /api/orders/{id}` — devuelve `pickupAddress` y `deliveryAddress` (D-1).
- `GET /auth/profile` — contraparte de lectura del nuevo `PUT`.
- `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`.
- `GET /catalog/categories`, `GET /catalog/products`.

### 3.4 Formato de respuesta

Todos los endpoints nuevos devuelven el envelope de `orders-service`/`delivery-service` (`ApiResponse<T>`) o el de `catalog-service`/`auth-service` según el caso existente. Errores usan `ApiError` de `shared-observability` (`{status, error, message}`).

---

## 4. Cambios por servicio

### 4.1 `auth-service`

**Nuevos archivos:**
- `application/port/inbound/UpdateUserProfileUseCase.java`
- `application/usecase/UpdateUserProfileService.java`
- `application/dto/UpdateUserProfileCommand.java`
- `infrastructure/adapter/inbound/rest/dto/UpdateProfileRequest.java`

**Archivos modificados:**
- `infrastructure/adapter/inbound/rest/AuthController.java` — agregar `@PutMapping("/profile")`. **Patrón**: validar el token en el controller con `validateToken.validate(bearer(authorization))` (mismo patrón que el `GET /auth/profile` existente; **no** crear un `JwtAuthFilter` — no existe en este servicio).
- `domain/model/User.java` — agregar método de dominio `public User conPerfil(String name, String lastName, String phone, String photo)` que devuelve una nueva instancia preservando `id, email, rut, roles, createdAt`. La clase es **inmutable** (`private final` en todos los campos) y `UserRepository.save(...)` hace upsert completo de todas las columnas — construir un `User` solo con los 4 campos editables tira `InvalidUserException("El email es obligatorio")`.
- `infrastructure/adapter/outbound/persistence/jpa/entity/UserEntity.java` — agregar `@PreUpdate` y mapear la columna `updated_at` (existe en V1 desde el alta pero no se actualiza). Sin migración de DB.
- `infrastructure/config/SecurityConfig.java` — agregar `.requestMatchers(HttpMethod.PUT, "/auth/profile").permitAll()` a la cadena. Sin esto el PUT cae en `anyRequest().denyAll()` → 403 silencioso sin pasar por `GlobalExceptionHandler`. El `permitAll` es correcto: en este servicio la auth real la hace el controller (`validateToken.validate(...)`), no Spring Security.
- `infrastructure/config/UseCaseConfiguration.java` — registrar el nuevo use case.

**Sin migración de DB.** Nota: la columna `users.updated_at` (V1 línea 24) existe en el esquema pero no se actualiza sola — necesita `@PreUpdate` en `UserEntity`. No requiere tocar SQL.

### 4.2 `delivery-service`

> **Feedback aplicado** (Delivery dev, 2026-09-25, contra `main`):
> 1. **Cero cambios en `delivery-service`**: la auditoría de código confirmó que `ClaimDeliveryOrdersUseCaseImpl` **nunca mutó el estado de la orden**. Solo asigna el repartidor a la ruta (`RouteStatus.ASSIGNED`) y delega el claim por HTTP a `orders-service` (`internalOrdersClient.claimOrders`).
> 2. `OrderServicePort` no tiene método `claim` (solo consulta pedidos por ID); la delegación de claim sale por `InternalOrdersClientPort` con `{userId, orderIds}` sin noción de estados.
> 3. En `delivery-service` no existe la entidad `Order` ni el método `Order.assignDelivery()`.
> 4. Toda la mutación de estado (`orderRepository.claimOrders(..., EN_CAMINO)` y `deliveryPort.updateRouteStatus(..., EN_CAMINO)`) reside en `orders-service/ClaimDeliveryOrdersUseCase.java`. Por ende, el trabajo de claim debe ser implementado y testeado 100% en `orders-service`.
> 5. Para respetar los boundaries de servicio (AGENTS.md), el PR de claim (`PR-orders-claim`, antes `PR-delivery`) pertenece al dev de `orders-service`. El dev de `delivery-service` no tiene cambios de código que commitear.

**Sin archivos nuevos ni modificados en delivery-service.**

**Sin migración de DB.**

### 4.3 `orders-service`

> **Feedback aplicado** (Felipe, 2026-09-24, contra `main @ afc8f0a`):
> 1. `Order.validateStatusTransition()` actual solo rechaza `ENTREGADO → *`. Permite, por ejemplo, `NUEVO_PEDIDO → ENTREGADO` directo. Falta matrix completa `from × to`.
> 2. `UpdateOrderStatusUseCase` no valida ownership del restaurante para rol `Restaurante` — IDOR: cualquier `Restaurante` puede cambiar el estado de cualquier pedido.
> 3. `Order.assignDelivery()` es código muerto. La mutación real está en `ClaimDeliveryOrdersUseCase.execute()` líneas 91-98. Ver §4.2.
> 4. Naming inconsistente: plan §4.3 decía `findByRestaurantAndStatusInRange`, tasks T-19 dice `findByRestaurantAndStatusAndCreatedAtBetween`. **Se unifica al segundo.**
> 5. `openapi.yaml` debe actualizarse en cada PR de orders-service que agregue endpoints (no se mencionó en el plan original).
> 6. `IdConverter.toUuid(restaurantIdLong)` debe aplicarse consistentemente en endpoints con `restaurant_id` como `Long` en query params (patrón ya usado en `OrderController.listOrders` línea 64).

**Nuevos archivos:**
- `application/usecase/ListAvailableOrdersUseCase.java`
- `application/usecase/GetRestaurantSalesSummaryUseCase.java`
- `application/dto/SalesSummaryResponse.java`
- `infrastructure/api/AvailableDeliveryOrdersController.java` (o agregar a `OrderController`)
- `infrastructure/api/SalesSummaryController.java` (o agregar a `OrderController`)
- `domain/exception/InvalidStatusTransitionException.java` (si no existe)
- `application/port/outbound/RestaurantMetricsRepository.java` (si se prefiere segregar)
- `application/usecase/ValidateOrderTransitionUseCase.java` (NUEVO) — encapsula la validación `from × to` antes de persistir.
- `application/port/outbound/RestaurantOwnershipPort.java` (NUEVO) — para que orders resuelva ownership vía HTTP a catalog (`CatalogHttpClientAdapter` ya existe; este es el puerto del lado de orders).

**Archivos modificados:**
- `infrastructure/api/OrderController.java` — agregar authz por rol en `PUT /api/orders/{id}/status`. Cargar matriz rol → transiciones permitidas. Agregar `IdConverter.toUuid(restaurantIdLong)` en handlers con `restaurant_id` (patrón existente en `listOrders`).
- `application/usecase/UpdateOrderStatusUseCase.java` — incorporar `currentUserRole` + `currentUserId` y validar contra la matriz. Para rol `Restaurante`, validar que `order.getRestaurantId() == ownershipPort.resolveRestaurantId(currentUserId)` (403 si no). Devolver `AccessDeniedException` si rol no autorizado, `OrderDomainException` si transición inválida.
- `domain/model/Order.java` — **revisar `validateStatusTransition(newStatus)`**: actualmente solo rechaza `ENTREGADO → *`. Agregar matrix completa `from × to`:
  - `NUEVO_PEDIDO → PREPARANDO` (válido)
  - `NUEVO_PEDIDO → LISTO_PARA_RETIRO` (corto-circuito, válido si la tienda decide saltarse PREPARANDO)
  - `PREPARANDO → LISTO_PARA_RETIRO` (válido)
  - `LISTO_PARA_RETIRO → RETIRADO` (válido, pickup confirmado)
  - `LISTO_PARA_RETIRO → EN_CAMINO` (legacy — permitido pero deprecated)
  - `RETIRADO → ENTREGADO` (válido)
  - `RETIRADO → EN_CAMINO` (legacy — permitido pero deprecated)
  - `ENTREGADO → *` rechazado (estado terminal)
  - Cualquier otra transición rechazada con 409 (`OrderDomainException`).
  - `isClaimable()` también debe actualizarse.
- `application/port/outbound/OrderRepositoryPort.java` — unificar método a `findByRestaurantAndStatusAndCreatedAtBetween(UUID restaurantId, Collection<OrderStatus> statuses, OffsetDateTime from, OffsetDateTime to)` (alineado con tasks T-19).
- `openapi.yaml` — actualizar en cada PR que agregue endpoint (PR-orders-status-authz, PR-orders-available, PR-orders-metrics).
- `ClaimDeliveryOrdersUseCase.java` — **modificar en PR-delivery**: la mutación a `EN_CAMINO` ya no debe ocurrir; el `claim` solo persiste `deliveryId` y deja el estado como está. Tests confirman que `Order.status` queda en `LISTO_PARA_RETIRO` post-claim.

**Sin migración de DB.**

### 4.4 `catalog-service`

> **Feedback aplicado** (Javier, 2026-09-24, contra `main @ afc8f0a`):
> 1. `RestaurantOwnershipResolver` + HTTP self-call + cache Caffeine → **eliminar**. Usar directamente `GetRestaurantByUserIdUseCase` existente (puerto local). El resolver HTTP se mantiene solo para que **otros** microservicios (orders) consulten catalog.
> 2. Catalog **NO tiene** Spring Security actualmente. El plan asume `store_owner` pero los roles reales son `Cliente`/`Restaurante`/`Repartidor`. Se debe agregar `spring-boot-starter-security`, validar JWT RS256 contra JWKS de Auth y autorizar `Restaurante`.
> 3. `products.image` es `varchar(255)`, NO `TEXT`. Plan de object key estable en lugar de URL firmada.
> 4. `ListProductsUseCase` no filtra `is_available`. El catálogo público debe filtrar; `/my/*` puede devolver todos.
> 5. `RestExceptionHandler` actual no cubre 401/403/413/502. Agregar mapeos.
> 6. Build integrado del monorepo está roto (Spring Boot 3.3.5 raíz vs 3.5.16 catalog).

**Nuevos archivos (corregidos):**
- `infrastructure/adapter/inbound/rest/MyStoreProductController.java`
- `infrastructure/adapter/inbound/rest/MyStoreImageController.java`
- `application/usecase/OwnerCreateProductUseCase.java` (wrapper sobre `CreateProductUseCase` con ownership derivada)
- `application/usecase/OwnerListProductsUseCase.java`
- `application/usecase/OwnerUpdateProductUseCase.java` (wrapper sobre `UpdateProductUseCase`, impide cambiar `restaurantId`)
- `application/usecase/OwnerDeleteProductUseCase.java`
- `application/usecase/UploadProductImageUseCase.java`
- `infrastructure/adapter/outbound/storage/S3ProductImageStorage.java`
- `infrastructure/config/StorageConfig.java`
- `application/dto/UploadImageResponse.java`
- `infrastructure/adapter/inbound/rest/dto/OwnerCreateProductRequest.java` (sin `restaurantId`)
- `infrastructure/adapter/inbound/rest/dto/OwnerUpdateProductRequest.java` (sin `restaurantId`)

**Archivos modificados:**
- `build.gradle.kts` — agregar `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `aws-sdk-java-v2 s3/netty/auth`.
- `infrastructure/config/SecurityConfig.java` (NUEVO) — validar JWT RS256 contra JWKS de Auth (`AUTH_JWKS_URI`). `requestMatchers("/api/catalog/my/**")` requiere rol `Restaurante`. `requestMatchers("/catalog/**", "/api/internal/**")` permitAll (InternalApiKeyFilter cubre `/api/internal/**`). `requestMatchers("/actuator/health/**", "/actuator/info")` permitAll.
- `application/usecase/GetRestaurantByUserIdUseCase.java` — **reutilizar** directamente para resolver `restaurantId` desde `userId` (no crear nuevo resolver).
- `application/usecase/CreateProductUseCase.java` — **NO se reutiliza para owner**. `MyStoreProductController` invoca wrappers owner que validan ownership.
- `application/usecase/UpdateProductUseCase.java` — **NO se reutiliza para owner** (debe ignorar `restaurantId` del body y mantener el derivado del JWT).
- `application/usecase/ListProductsUseCase.java` — agregar métodos `findAllAvailable()` y `findByRestaurantIdAndAvailableTrue(...)` que filtren por `isAvailable=true`. El catálogo público (`GET /catalog/products`, `GET /catalog/products/{id}`) debe usar estos; el endpoint owner (`GET /api/catalog/my/products`) puede usar `findAll` / `findByRestaurantId` sin filtro para permitir ver también los desactivados.
- `infrastructure/adapter/outbound/persistence/jpa/repository/SpringDataProductRepository.java` — agregar queries derivadas `findAllByIsAvailableTrue`, `findByCategoryIdAndIsAvailableTrue`, `findByRestaurantIdAndIsAvailableTrue`.
- `infrastructure/adapter/inbound/rest/RestExceptionHandler.java` — agregar handlers para: `AuthenticationException` → 401, `AccessDeniedException` → 403, `MaxUploadSizeExceededException` → 413, `ImageStorageException` → 502. Decidir si conservar `ErrorResponse` propio de catalog o adoptar `ApiError` de `shared-observability` (recomendado para consistencia entre microservicios).

**Sobre `products.image`:**
- La columna actual es `varchar(255)`. NO es TEXT.
- **Estrategia recomendada**: persistir **object key estable** (p.ej. `products/{yyyy}/{mm}/{uuid}.webp`), NO URL firmada. Construir la URL pública o firmada al responder. Esto evita (a) URLs que expiran y quedan persistidas como referencia, (b) migración de columna.
- **Si se decide persistir URL completa** (no recomendado): agregar migración Flyway a `varchar(1024)` o `TEXT`. Esto invalida la afirmación "cero migraciones" de este change.

**Sin servicios nuevos.** `spring-boot-starter-security` se agrega a la dependencia existente.

### 4.5 `gateway`

> **Feedback aplicado** (Javier, 2026-09-24): el gateway **NO puede** transportar multipart de 5MB en el estado actual — Fastify no tiene parser multipart, `engine.ts` serializa body con `JSON.stringify`, y el límite por defecto es inferior a 5MB. PR-gateway-2 **debe incluir código**, no solo YAML.

**`PR-gateway-1`** (profile-and-delivery) — sigue siendo **solo config YAML** (1 ruta nueva a orders, sin multipart).

**`PR-gateway-2`** (store-flow) — **incluye código nuevo** además de YAML:

- Registrar parser multipart (`@fastify/multipart` o equivalente) y aumentar `bodyLimit` a ≥ 6MB para soportar imagen 5MB + overhead.
- Modificar `engine.ts` (o nuevo módulo) para **passthrough de bodies multipart** sin `JSON.stringify` — preservar `Content-Type` con boundary, `Content-Length`/`Transfer-Encoding` correctos.
- Registrar rutas:
  - `/api/orders/restaurants/{id}/sales-summary` → `orders-service:8083`
  - `/api/catalog/my/products` (POST/GET/PUT/DELETE) → `catalog-service:8082`
  - `/api/catalog/my/products/image` (POST multipart) → `catalog-service:8082`
- Agregar test de integración que envíe una imagen real a través del gateway y valide que llega intacta al backend.

(Las rutas existentes `/api/auth/*`, `/api/orders/*`, `/api/catalog/*`, `/api/delivery/*` siguen iguales.)

---

## 5. Datos y migraciones

**No se agregan migraciones Flyway.** Todas las tablas y columnas necesarias ya existen:

- `users` (auth) — `name`, `last_name`, `phone`, `photo` ya están en el esquema V1.
- `products` (catalog) — **`image` es `varchar(255)`, NO `TEXT`**. Si se persiste URL firmada, agregar migración a `varchar(1024)` o `TEXT`. Recomendación: persistir object key estable y construir URL al responder.
- `orders` (orders) — `status`, `restaurant_id`, `created_at` ya indexados o indexables.
- `delivery_routes` (delivery) — `order_id`, `delivery_person_id` ya disponibles.
- **`auth-service.users.updated_at`** (V1 línea 24) — la columna existe desde el alta con `default now()`, pero no está mapeada en `UserEntity` ni tiene trigger. **El fix es en código Java (`@PreUpdate` en la entidad), no en SQL.** Cumple la promesa de "sin migración".
- **`orders.status` admite `EN_CAMINO`** (legacy) — después de PR-delivery no se asignan nuevos pedidos a `EN_CAMINO` directamente. El flujo nuevo es `LISTO_PARA_RETIRO → RETIRADO` (pickup confirmado) → `ENTREGADO`. **Los pedidos legacy que ya están en `EN_CAMINO` se mantienen en la BD**; ningún código los transiciona automáticamente a `RETIRADO`. Decisión: el ciclo de vida legacy queda congelado; pedidos en `EN_CAMINO` eventualmente pasan a `ENTREGADO` por flujo normal. `RETIRADO` también se acepta como `from` válido en `validateStatusTransition` para legacy.

**Índice (decisión de PR-orders-metrics, con owner explícito):** el dev de orders mide EXPLAIN con dataset representativo (dataset de prueba con ≥10k órdenes por restaurante) durante PR-orders-metrics. **Owner**: dev de orders ejecutando PR-orders-metrics. Si el índice se justifica (`CREATE INDEX IF NOT EXISTS idx_orders_restaurant_status_created ON orders(restaurant_id, status, created_at DESC)`), se agrega como migración Flyway en el mismo PR. **Si no se mide, el índice no se agrega** (mantener default: cero migraciones). No queda en el limbo.

---

## 6. Estrategia de implementación — dos OpenSpec changes paralelos

Cada OpenSpec change es **autónomamente testeable y deployable**. Los dos cambios NO se pisan entre sí. La subdivisión interna es por servicio (un PR por dueño de servicio) para maximizar paralelismo.

### 6.1 OpenSpec change #1 — `profile-and-delivery`

| PR | Servicio | Contenido | Bloquea |
|---|---|---|---|
| `PR-auth` | auth-service | `PUT /auth/profile` con use case + DTO + controller + tests | — |
| `PR-orders-status-authz` | orders-service | Matriz rol→transición-permitida en `PUT /api/orders/{id}/status` + tests de la matriz | `PR-gateway-1` |
| `PR-orders-available` | orders-service | `GET /api/orders/available-for-delivery` (use case + controller + tests) | `PR-gateway-1` |
| `PR-delivery` | orders-service | Quitar mutación de status a `EN_CAMINO` en `ClaimDeliveryOrdersUseCase.execute()` (líneas 91-98). Tests confirman que `Order.status` queda en `LISTO_PARA_RETIRO` post-claim. `Order.assignDelivery()` es código muerto, no se modifica. Coordinar con dev de delivery antes de merge | — |
| `PR-gateway-1` | gateway | 2 rutas nuevas (`/api/orders/available-for-delivery` → orders; validar `/api/orders/{id}/status` ya estaba) | último |

**Trabajo en paralelo:** los 4 PRs de servicio pueden arrancar en paralelo (archivos disjuntos).

### 6.2 OpenSpec change #2 — `store-flow`

| PR | Servicio | Contenido | Bloquea |
|---|---|---|---|
| `PR-catalog-image` | catalog-service | S3/MinIO client config + variables de entorno + `POST /api/catalog/my/products/image` + tests | `PR-catalog-products` |
| `PR-catalog-products` | catalog-service | `/api/catalog/my/products` CRUD (POST/GET/PUT/DELETE) con ownership derivado del JWT + tests | `PR-gateway-2` |
| `PR-orders-metrics` | orders-service | `GET /api/orders/restaurants/{id}/sales-summary` con agregaciones + tests | `PR-gateway-2` |
| `PR-gateway-2` | gateway | 2 rutas nuevas (1 a catalog para `/api/catalog/my/*`, 1 a orders para `/api/orders/restaurants/{id}/sales-summary`) | último |

**Trabajo en paralelo:** `catalog-image` arranca primero (es prerrequisito técnico); `orders-metrics` arranca en paralelo desde el inicio.

### 6.3 Línea de tiempo ideal

```
Change #1:
  PR-auth      ─────────►
  PR-orders-1a ─────────►
  PR-orders-1b ─────────►
  PR-delivery  ─────────►
  PR-gateway-1 ────────────────────►

Change #2:
  PR-catalog-image ─► PR-catalog-products ─────────────►
  PR-orders-metrics ────────────────────────►
  PR-gateway-2 ────────────────────────────────────────►
```

**Cinco devs pueden tocar sus servicios en paralelo** sin bloquearse. Solo el dev de gateway espera, lo cual es inevitable.

---

## 7. Flujos de datos (resumen)

### 7.1 `PUT /auth/profile`
```
Flutter → Gateway → auth-service
                 → SecurityConfig permite PUT /auth/profile (permitAll)
                 → AuthController.profilePut():
                    ├─ validateToken.validate(bearer(authorization)) → TokenClaims
                    │   (mismo patrón que GET /auth/profile — NO hay JwtAuthFilter en auth-service)
                    └─ UpdateUserProfileUseCase(claims.userId, dto)
                       ├─ users.findById(userId) → User existente (404 si no)
                       ├─ user.conPerfil(dto.name, dto.lastName, dto.phone, dto.photo)
                       │   (método de dominio: preserva email, rut, roles, createdAt)
                       ├─ Validar phone no colisiona con otro user (409 si choca;
                       │   ya manejado por GlobalExceptionHandler.handleConflictoDeDatos)
                       └─ users.save(userModificado) → User actualizado
                 → 200 UserProfile (envuelto en ApiResponse)
```

**Nota sobre edición de email**: a pesar de menciones contradictorias en versiones previas de este documento, **email NO es editable** vía `PUT /auth/profile` (queda fuera de alcance; ver §10). Si se decidiera agregar edición de email en el futuro, sería un PR aparte con flujo de verificación y actualización de `login.login` (que actualmente es UNIQUE y se inicializa con `email.value()` en `RegisterUserService`).

### 7.2 `GET /api/orders/available-for-delivery?restaurant_id=X&limit=5`
```
Flutter → Gateway → orders-service
                 → JwtAuthFilter valida JWT (rol delivery)
                 → ListAvailableOrdersUseCase(restaurantId, limit)
                    ├─ OrderRepositoryPort.findByRestaurantAndStatus(
                    │      restaurantId, LISTO_PARA_RETIRO, limit)
                    └─ OrderEnricher.enrich(...) (datos de cliente)
                 → 200 [OrderListResponse]
```

### 7.3 `PUT /api/orders/{id}/status`
```
Flutter → Gateway → orders-service
                 → JwtAuthFilter extrae userId + rol
                 → UpdateOrderStatusUseCase(orderId, newStatus, rol)
                    ├─ OrderRepositoryPort.findById(orderId)
                    ├─ Matriz authz: rol → transiciones permitidas
                    │   └─ 403 si no autorizado
                    ├─ order.validateStatusTransition(newStatus)
                    │   └─ 409 si transición inválida
                    └─ OrderRepositoryPort.save(order)
                 → 200 OK
```

### 7.4 `GET /api/orders/restaurants/{restaurantId}/sales-summary`
```
Flutter → Gateway → orders-service
                 → JwtAuthFilter extrae userId + rol
                 → GetRestaurantSalesSummaryUseCase(restaurantId, range, currentUserId)
                    ├─ Validar que currentUserId es dueño de restaurantId (403 si no)
                    ├─ Calcular rango temporal (from/to según day/week/month)
                    ├─ OrderRepositoryPort.findByRestaurantAndStatusInRange(...)
                    ├─ Agregaciones: totalOrders, totalRevenue, avgTicket, topProducts
                    └─ Construir SalesSummaryResponse
                 → 200 SalesSummaryResponse
```

### 7.5 `/api/catalog/my/products` (CRUD)
```
Flutter → Gateway → catalog-service
                 → JwtAuthFilter extrae userId + rol store_owner
                 → MyStoreProductController
                    ├─ RestaurantOwnershipResolver.resolve(userId)
                    │   └─ HTTP GET /api/internal/restaurants?userId={userId}
                    │      → restaurantId (cache TTL 60s)
                    ├─ Validar ownership (403 si producto.restaurantId != resolved)
                    ├─ CRUD JPA normal
                    └─ 200/201/204
```

### 7.6 `POST /api/catalog/my/products/image`
```
Flutter → Gateway → catalog-service
                 → JwtAuthFilter valida JWT (rol store_owner)
                 → Validar multipart: tamaño ≤ 5MB, MIME ∈ {jpeg, png, webp}
                 → S3ProductImageStorage.upload(bucket, key, bytes, contentType)
                    └─ 502 si S3 falla
                 → 201 { url }
(el cliente luego llama POST /api/catalog/my/products con ese url en image)
```

---

## 8. Matriz de errores

| Código | Cuándo |
|---|---|
| **400** | Body inválido, MIME no permitido, tamaño excedido |
| **401** | JWT ausente o expirado |
| **403** | Rol no autorizado / IDOR (repartidor intenta `PUT /status` con `NUEVO_PEDIDO`; tienda intenta editar producto de otro restaurante) |
| **404** | Recurso no existe |
| **409** | Transición de estado inválida |
| **413** | Payload > límite (imagen > 5MB) |
| **502** | Dependencia externa caída (S3/MinIO) |
| **503** | Servicio abajo (lo emite gateway) |

Todos en formato `ApiError` de `shared-observability`.

---

## 9. Estrategia de testing

### Unit tests (sin Docker, corren en CI cada PR)
- `UpdateUserProfileUseCaseTest` (auth)
- `ListAvailableOrdersUseCaseTest` (orders)
- `UpdateOrderStatusUseCaseTest` con matriz rol × transición (orders)
- `GetRestaurantSalesSummaryUseCaseTest` (orders)
- `OwnerProductUseCasesTest` x 4 CRUD (catalog)
- `UploadProductImageUseCaseTest` con stub de S3 (catalog)
- `ClaimDeliveryOrdersUseCaseTest` ajustado: verificar que **no** se modifica `Order.status` ni ruta a `EN_CAMINO` post-claim (orders)
- `RestaurantOwnershipResolverTest` con stub HTTP (catalog)

### Integration tests `*IT.java` (test containers, CI los corre)
- `AuthControllerIT` — `PUT /auth/profile` con perfil válido, email duplicado, JWT inválido.
- `OrderControllerIT` — matriz 2D (rol × transición) para `PUT /api/orders/{id}/status`.
- `AvailableDeliveryOrdersIT` — `GET /api/orders/available-for-delivery` filtrando correctamente por estado y limitando resultados.
- `RestaurantMetricsIT` — agregaciones correctas, validación de ownership.
- `CatalogMyProductsIT` — CRUD con ownership, intento de IDOR devuelve 403.
- `CatalogImageUploadIT` — multipart válido, MIME inválido, tamaño excedido, S3 caído → 502 (con LocalStack o stub).

### Smoke tests (opcional, post-merge)
- Script en `gateway/tests/` que ejercita los nuevos endpoints en dev.

---

## 10. Fuera de alcance (explícito)

1. Edición de `vehicle` para repartidor desde la app.
2. `range=custom` con `from`/`to` en métricas — solo `day`/`week`/`month`.
3. Upload de foto de perfil (`User.photo`) — se queda como URL string. Pendiente un OpenSpec de uploads compartidos.
4. Mapa / geocoding / ruta computada en backend — solo direcciones en strings (D-1).
5. Hard delete de productos — solo soft delete (`available=false`).
6. Soporte para dueño de múltiples restaurantes.
7. Reconciliación de estado entre `orders-service` y `delivery-service` post-claim — el `claim` actualiza `deliveryId` vía `internalOrdersClient` con flag; queda intacto.
8. Notificaciones push al cliente cuando cambia el estado del pedido.
9. Endpoints admin (cambiar `vehicle`, desactivar tienda, etc.).
10. Versionado de API (`/v1`, `/v2`).

---

## 11. Riesgos y mitigaciones

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | El PR de `delivery-service` (quitar cambio de status en claim) cambia comportamiento — clientes viejos de Flutter que asumen `EN_CAMINO` post-claim pueden romperse | Coordinar con dev de Flutter antes del merge; documentar en `tasks.md` del OpenSpec que la app debe transicionar manualmente al primer `RETIRADO` |
| 2 | ~~Cache `userId → restaurantId` en catalog-service puede quedar stale~~ **Eliminado**: el plan corregido usa `GetRestaurantByUserIdUseCase` local, sin cache HTTP self-call | N/A |
| 3 | S3/MinIO en Floci **NO está aprovisionado** para catalog (`infra/floci/INFRASTRUCTURE.md` marca como `not used`). No hay bucket, vars S3, secretos ni task definition | Antes de PR-catalog-image: crear bucket S3 en Floci, endpoint accesible desde el contenedor, credenciales/rol, política de lectura, CORS, vars en `env.shared.template`, task definition ECS y config local |
| 4 | **Build integrado del monorepo está roto** para catalog: `services/build.gradle.kts` fija Spring Boot 3.3.5, `services/catalog-service/build.gradle.kts` declara 3.5.16. `services/gradlew.bat :catalog-service:test` FAIL por conflicto | Tarea previa: alinear versión de Spring Boot O retirar catalog del build raíz. Agregar `catalog-service-ci.yml` que ejecute tests autónomos |
| 5 | **Catalog no tiene Spring Security**. Plan asume `requestMatchers` con `store_owner`, pero (a) dependencia no está, (b) `SecurityConfig` no existe, (c) rol real es `Restaurante` (no `store_owner`), (d) gateway no reenvía `roles[]` en claims por defecto | Agregar `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`. Implementar `SecurityConfig` que valida JWT RS256 contra JWKS de Auth y mapea `roles` a authorities. Definir convención de roles única |
| 6 | **Gateway no soporta multipart** para `/api/catalog/my/products/image`. `engine.ts` serializa con `JSON.stringify`, no hay `@fastify/multipart` en deps, bodyLimit insuficiente | PR-gateway-2 debe incluir código real: parser multipart, bodyLimit ≥ 6MB, passthrough raw stream, preservar `Content-Type` con boundary |
| 7 | **Soft delete no oculta del catálogo público**. `ListProductsUseCase` usa `findAll`/`findByCategoryId`/`findByRestaurantId` sin filtrar `is_available`. Si se implementa el DELETE sin esto, productos desactivados siguen visibles | Agregar queries `...AndIsAvailableTrue` y métodos separados `findAllAvailable()`, `findByRestaurantIdAndAvailableTrue(...)`. Usarlos en endpoints públicos. `/api/catalog/my/*` puede usar los métodos sin filtro para ver desactivados |
| 8 | **Contrato de errores inconsistente**. Plan exige `ApiError` de shared-observability, pero catalog usa `ErrorResponse` propio y `RestExceptionHandler` no cubre 401/403/413/502 | Decisión: adoptar `ApiError` para consistencia con auth/orders/delivery (recomendado), o mantener `ErrorResponse` y documentar la divergencia. Agregar handlers específicos |
| 9 | Autorización por rol depende de que el JWT traiga `roles[]`. Si no está, la authz falla cerrado | Verificar que `RegisterUserUseCase` asigne roles correctamente; tests cubren "JWT sin roles" |
| 10 | Métricas con `LISTO_PARA_RETIRO` muy alto en alguna tienda → query lenta | Evaluar índice `orders(restaurant_id, status, created_at)` en PR-orders-metrics |
| 11 | Tests de integración contra S3 real son flaky | Usar LocalStack o stub in-memory; documentar |
| 12 | El PR-gateway-1 depende de que PR-orders-status-authz y PR-orders-available estén mergeados. Si un dev lo mergea antes, las rutas devuelven 404 | PR-gateway-1 va al final, después de que los otros estén mergeados a `main` |
| 13 | catalog-image y catalog-products se mergean en paralelo y ambos tocan `build.gradle.kts` (Spring Security + AWS SDK) y `SecurityConfig` → conflicto | catalog-products depende técnicamente de catalog-image (comparten `build.gradle.kts`, `SecurityConfig`, `application.yml`); el dev de catalog los mergea secuencialmente en su orden interno |
| 14 | **`Order.validateStatusTransition()` actual está incompleto** (solo rechaza `ENTREGADO → *`). Permite, por ejemplo, `NUEVO_PEDIDO → ENTREGADO` directo. El spec dice "409 si la transición no es válida por el estado actual" pero la lógica para emitir ese 409 no existe | PR-orders-status-authz agrega matrix completa `from × to` en `Order.validateStatusTransition()`. Validaciones unitarias exhaustivas para cada par inválido |
| 15 | **IDOR en `updateOrderStatus` para rol `Restaurante`**: cualquier `Restaurante` con JWT puede cambiar el estado de cualquier pedido, no solo de su restaurante | PR-orders-status-authz valida que `order.getRestaurantId() == ownershipPort.resolveRestaurantId(currentUserId)` antes de aplicar el cambio (403 si no). Test IT explícito del caso IDOR |
| 16 | **`openapi.yaml` queda desactualizado**. Si bien el plan dice "sin código nuevo en gateway, solo config", en orders-service los 3 PRs agregan endpoints. El OpenAPI debe actualizarse en el mismo PR o queda drift con la implementación | Cada PR de orders-service que agregue endpoint incluye commit `docs(openapi): update openapi.yaml with new endpoint`. Code review del PR verifica |
| 17 | **`EN_CAMINO` queda como estado legacy**. Después de PR-delivery, no se asignan nuevos pedidos a `EN_CAMINO`. Pedidos ya en ese estado siguen en la BD; `validateStatusTransition` debe permitir `EN_CAMINO → ENTREGADO` (legacy) | Documentar en `Order.java` que `EN_CAMINO` es legacy. `validateStatusTransition` permite transiciones `EN_CAMINO → RETIRADO`, `EN_CAMINO → ENTREGADO` y viceversa con `RETIRADO`. Pedidos en `EN_CAMINO` se procesan por flujo normal sin migración de datos |

---

## 12. Próximos pasos

Esta sesión **solo planeó**. Cuando se apruebe este doc:

1. **Crear los dos OpenSpec changes** (`openspec/changes/profile-and-delivery/` y `openspec/changes/store-flow/`) con `proposal.md`, `tasks.md`, `spec.md` y `design.md` — trabajo de esta misma sesión (siguiente paso inmediato).
2. **Asignar los PRs** a los dueños de cada servicio según la tabla §6.
3. **Coordinar con el dev de Flutter** el cambio de comportamiento del `claim` antes del merge del PR-delivery (riesgo #1).
4. **Configurar el bucket S3/MinIO** en Floci antes del PR-catalog-image (riesgo #3).
6. **Mergear en orden** los PRs internos de cada change (PR-gateway-* al final de cada uno).

---

**Sesión de brainstorming cerrada.** Esperando confirmación para commitear este doc y los dos OpenSpec changes al repo.