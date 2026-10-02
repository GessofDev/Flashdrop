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
| `gateway` | Rutas nuevas solo para catalog; las de orders ya están cubiertas por el prefijo `/api/orders` |

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
| `/api/orders/available-for-delivery` | `GET` | orders-service | JWT (rol `Repartidor`) | — (query: `restaurant_id` req, `limit` opcional default 5) | `[OrderListResponse]` (pedidos en LISTO_PARA_RETIRO sin repartidor asignado, orden FIFO, `limit` entre 1 y 50) |
| `/api/orders/restaurants/{restaurantId}/sales-summary` | `GET` | orders-service | JWT (rol `Restaurante`) | — (query: `range` opcional `day`/`week`/`month` default `week`) | `SalesSummaryResponse` dentro de `ApiResponse<T>` (`{success, data}`); `restaurantId` Long, `productId` UUID |
| `/api/catalog/my/products` | `POST` | catalog-service | JWT (rol `Restaurante`) | `{categoryId, name, description, price, image, available}` (sin `restaurantId`) | `ProductResponse` |
| `/api/catalog/my/products` | `GET` | catalog-service | JWT (rol `Restaurante`) | — | `[ProductResponse]` |
| `/api/catalog/my/products/{productId}` | `PUT` | catalog-service | JWT (rol `Restaurante`) | `{categoryId, name, description, price, image, available}` | `ProductResponse` |
| `/api/catalog/my/products/{productId}` | `DELETE` | catalog-service | JWT (rol `Restaurante`) | — | 204 No Content |
| `/api/catalog/my/products/image` | `POST` | catalog-service | JWT (rol `Restaurante`) | `multipart/form-data` campo `file` (jpeg/png/webp, ≤5MB) | `{url}` |

> **Convención de nombres de rol:** los roles reales en `auth-service` son `Cliente`, `Restaurante`, `Repartidor` (ver `auth-service/src/main/resources/db/seed/V2__seed_development.sql`). Estos 3 son los únicos roles existentes (no hay admin). Los nombres tentativos previos (`store_owner`, `delivery`, `customer`) **se descartan** en todo este documento. Esta convención es bloqueada por el fix de `PR-orders-jwt-roles` (ver §4.3.0); si no se lee correctamente del JWT, ningún chequeo `@PreAuthorize("hasRole('Restaurante')")` va a funcionar.

### 3.2 Endpoints MODIFICADOS (mismo path)

| Path | Cambio |
|---|---|
| `PUT /api/orders/{id}/status` | Restaurante puede fijar `PREPARANDO` y `LISTO_PARA_RETIRO`, solo en pedidos de su restaurante. Repartidor puede fijar `RETIRADO` y `ENTREGADO`, solo en pedidos asignados a él. Cliente no puede cambiar estados. El estado actual decide si se puede llegar al nuevo (matriz de §4.3). 403 si el rol no puede fijar ese estado o el pedido no es suyo; 409 si su rol puede fijar ese estado, pero el estado actual del pedido no lo permite. |
| `POST /delivery/claim` | El use case **deja de cambiar** `Order.status`. Solo asigna `deliveryId`. Un pedido con repartidor asignado ya no se puede volver a tomar. No sincroniza el estado de la ruta. El estado del pedido queda en `LISTO_PARA_RETIRO` hasta que el repartidor confirme el pickup con un PUT subsecuente. |

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

**Sin archivos nuevos ni modificados en delivery-service.** Único cambio de configuración: activar la variable `DELIVERY_CLAIM_DELEGATE_TO_ORDERS=true` en FloCI (`infra/floci/task-definitions/delivery-service.dev.json`) para que el claim delegue a orders-service.

**Sin migración de DB.**

### 4.3 `orders-service`

> **Feedback aplicado** (Felipe, 2026-09-24, contra `main @ afc8f0a`):
> 1. `Order.validateStatusTransition()` actual solo rechaza `ENTREGADO → *`. Permite, por ejemplo, `NUEVO_PEDIDO → ENTREGADO` directo. Falta matrix completa `from × to`.
> 2. `UpdateOrderStatusUseCase` no valida ownership del restaurante para rol `Restaurante` — IDOR: cualquier `Restaurante` puede cambiar el estado de cualquier pedido.
> 3. `Order.assignDelivery()` es código muerto. La mutación real está en `ClaimDeliveryOrdersUseCase.execute()` líneas 91-98. Ver §4.2.
> 4. Naming inconsistente: plan §4.3 decía `findByRestaurantAndStatusInRange`, tasks T-19 dice `findByRestaurantAndStatusAndCreatedAtBetween`. **Se unifica al segundo.**
> 5. `openapi.yaml` debe actualizarse en cada PR de orders-service que agregue endpoints (no se mencionó en el plan original).
> 6. `IdConverter.toUuid(restaurantIdLong)` debe aplicarse consistentemente en endpoints con `restaurant_id` como `Long` en query params (patrón ya usado en `OrderController.listOrders` línea 64).

#### 4.3.0 Fix bloqueante previo — `PR-orders-jwt-roles` (lectura de roles del JWT)

> **Bloquea:** `PR-orders-status-authz`, `PR-orders-available`, `PR-orders-metrics`, `PR-catalog-products`. **No se puede implementar authz por rol de forma segura hasta que este PR esté mergeado.** Estimación: 30-60 min (alcance acotado, ya verificado contra `main @ afc8f0a`).

**Estado actual (bug)** — `config/JwtValidationFilter.java` líneas 86-91 arma el `UsernamePasswordAuthenticationToken` con `new ArrayList<>()` como authorities:

```java
if (validation.getStatusCode().is2xxSuccessful()) {
    String subject = extractSubject(token);   // solo lee 'sub'
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(subject, null, new ArrayList<>());
    //                                                              ^^^^^^^^^^^^^^
    //                                                              authorities VACÍAS
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
```

El emisor `auth-service/.../JwtTokenService.java:41` sí mete los roles (`.claim("roles", claims.roles())`), y `AuthController.validate()` (`/auth/validate`) retorna el `TokenClaims` completo con roles. El descarte ocurre 100% del lado consumidor: orders extrae solo `sub` y nunca toca el claim `roles`. Consecuencia: cualquier `@PreAuthorize("hasRole('Restaurante')")` falla cerrado (403 universal) o, peor, un chequeo manual que lee authorities vacías queda falla abierto (IDOR). Bloquea PR-orders-status-authz y los demás PRs que dependen de leer el rol.

**Decisiones de diseño** (firmadas en esta vuelta; no se reabren en code review salvo error factual):

1. **Cómo extraer los roles**: decodificar el payload del JWT **localmente** (mismo método `Base64.getUrlDecoder()` que ya usa el filtro para extraer `sub`) y leer el claim `"roles"`. **No** consumir el body JSON de `/auth/validate` — el filtro ya confía en la firma validada por Auth (status 2xx), así que la decodificación local es segura y evita un parseo extra. Si el claim está ausente o no es una lista, authorities queda vacía (mismo comportamiento que hoy; los chequeos de rol fallarán cerrado aguas abajo, que es lo correcto).
2. **Convención de prefijo**: `SimpleGrantedAuthority("ROLE_" + rol)`. Habilita `@PreAuthorize("hasRole('Restaurante')")` estándar de Spring Security sin mapeos custom. Documentar en el JavaDoc del filtro que el prefijo `ROLE_` se aplica acá; los chequeos en código deben usar `hasRole(...)` o `hasAuthority("ROLE_Restaurante")` consistentemente.
3. **Nombres de rol**: usar los nombres reales de la BD (`auth-service/.../V2__seed_development.sql`): `Cliente`, `Restaurante`, `Repartidor`. Esta convención reemplaza los nombres tentativos previos del plan (`store_owner`, `delivery`, `customer`). Toda referencia a roles en este documento usa los nombres reales.
4. **Reusabilidad del filtro**: NO se extrae a `shared-observability` en esta vuelta. `catalog-service` adoptará un enfoque distinto (§4.4 — Spring Security + JWKS local, validación con clave pública). Los dos enfoques son equivalentes en seguridad; unificar es un refactor posterior si surge duplicación real.
5. **Type safety**: `requireCurrentRoles()` retorna `Set<Role>` (todos los roles del usuario) y `hasRole(Role)`. Enum `Role { CLIENTE, RESTAURANTE, REPARTIDOR }` con el valor del claim (`Cliente`, `Restaurante`, `Repartidor`). No existe rol admin. `admin@demo.cl` es un usuario con los 3 roles; sus permisos son la suma de esos roles. Si el claim tiene un valor que no matchea el enum, `AccessDeniedException("Rol desconocido: <valor>")`.

**Archivos modificados** (todos en `services/orders-service/src/main/java/cl/flashdrop/orders/`):

- `config/JwtValidationFilter.java` — después de validar contra `/auth/validate` (2xx), decodificar el payload del JWT y leer el claim `"roles"`. Si está presente y es una lista, poblar `SimpleGrantedAuthority("ROLE_" + rol)` por cada elemento. Si está ausente o malformado, log warn + authorities vacía.
- `infrastructure/api/CurrentUserResolver.java` — agregar `requireCurrentRoles()` que retorna `Set<Role>` (todos los roles del usuario) y método helper `hasRole(Role)`. Lanza `AccessDeniedException("No autenticado")` o `AccessDeniedException("Rol desconocido: <x>")` según el caso. `requireCurrentUserId()` se mantiene igual (UUID vía `IdConverter.toUuid(long)`).
- `domain/model/Role.java` (NUEVO) — enum `Role { CLIENTE, RESTAURANTE, REPARTIDOR }` con el mapeo del claim (`Cliente`, `Restaurante`, `Repartidor`). Sin lógica, sin imports de framework. Cero impacto en DB.
- `config/SecurityConfig.java` — sin cambios estructurales. La authz fina por rol se aplica en cada controller / use case, no en la cadena global. Mantener `.authenticated()` como hoy.
- `application/usecase/UpdateOrderStatusUseCase.java` — incorporar `currentRoles` (`Set<Role>`) y `currentUserId` (`UUID`). Validar contra la política de roles y matriz de transiciones (§3.2). Para rol `Restaurante`, validar que `order.getRestaurantId() == catalogPort.findRestaurantIdByUserId(currentUserId)` (D10). Para rol `Repartidor`, validar que `order.getDeliveryId()` sea el delivery del usuario (`DeliveryPort.findDeliveryIdByUserId(currentUserId)`) (D8). Devolver `ForbiddenOperationException` (403) si rol no autorizado o IDOR, `OrderDomainException` si transición inválida (409).
- `infrastructure/api/OrderController.java` — pasar `currentUserResolver.requireCurrentRoles()` y `currentUserId()` a `UpdateOrderStatusUseCase`. Sin cambios en la firma HTTP.

**Sin migración de DB.** El cambio es 100% en código Java. No toca SQL.

**Tests nuevos** (detalle en §9):

- `JwtValidationFilterTest`: matriz (sin JWT → 401, JWT sin claim `roles` → authorities vacías, JWT con `["Restaurante"]` → `ROLE_Restaurante` en authorities, JWT con `["Cliente", "Repartidor"]` → dos authorities, JWT con claim `roles` mal formado — string en vez de lista — → log warn + authorities vacías).
- `CurrentUserResolverTest`: `requireCurrentRoles()` con `ROLE_Restaurante` → `Role.RESTAURANTE`; con `ROLE_Cliente` y `ROLE_Repartidor` → sus enums; sin authorities → `AccessDeniedException`; authority con valor que no matchea el enum → `AccessDeniedException("Rol desconocido: <x>")`.
- `UpdateOrderStatusUseCaseTest`: extender la matriz 2D con casos de (rol × transición) usando `CurrentUserResolver` mockeado.
- `SecurityIntegrationTest`: extender con casos end-to-end de un JWT real con `roles: ["Restaurante"]` accediendo a un endpoint protegido y verificando 200 vs 403 según el endpoint.

**Nuevos archivos (implementación real):**
- `domain/model/Role.java`, `domain/model/RoleTransitionPolicy.java`, `domain/model/SalesRange.java`
- `domain/exception/ForbiddenOperationException.java`, `domain/exception/StatusTransitionForbiddenException.java`
- `application/usecase/ListAvailableOrdersUseCase.java`, `application/usecase/GetRestaurantSalesSummaryUseCase.java`
- `application/dto/SalesSummary.java`
- `infrastructure/api/SalesSummaryController.java`, `infrastructure/api/dto/response/SalesSummaryResponse.java`
- `config/ClockConfig.java`

*(No se crearon `ValidateOrderTransitionUseCase.java`, `InvalidStatusTransitionException.java`, `RestaurantMetricsRepository.java`, `RestaurantOwnershipPort.java` ni `AvailableDeliveryOrdersController.java`. El endpoint de pedidos disponibles se agregó en `OrderController`, y el cálculo de métricas usa `Clock` inyectable y `CatalogPort` existente).*

**Archivos modificados:**
- `infrastructure/api/OrderController.java` — agregar authz por rol en `PUT /api/orders/{id}/status` y endpoint `GET /api/orders/available-for-delivery`. Validar rol `Repartidor` con `hasRole` (D18). Pasar roles y `currentUserId` a los use cases.
- `application/usecase/UpdateOrderStatusUseCase.java` — incorporar `Set<Role> currentRoles` + `UUID currentUserId`. Validar rol primero con `RoleTransitionPolicy` (403), luego ownership (403 si Restaurante y no es dueño según `catalogPort.findRestaurantIdByUserId(currentUserId)`; 403 si Repartidor y el pedido no está asignado a él según `deliveryPort.findDeliveryIdByUserId(currentUserId)`), y finalmente transición con `order.validateStatusTransition(newStatus)` (409) (D5, D8, D9, D10, D11).
- `domain/model/Order.java` — matriz completa `from × to`:
  - `NUEVO_PEDIDO → PREPARANDO` (válido)
  - `NUEVO_PEDIDO → LISTO_PARA_RETIRO` (corto-circuito, válido si la tienda decide saltarse PREPARANDO)
  - `PREPARANDO → LISTO_PARA_RETIRO` (válido)
  - `LISTO_PARA_RETIRO → RETIRADO` (válido, pickup confirmado)
  - `LISTO_PARA_RETIRO → EN_CAMINO` (legacy — permitido pero deprecated)
  - `RETIRADO → ENTREGADO` (válido)
  - `RETIRADO → EN_CAMINO` (legacy — permitido pero deprecated)
  - `EN_CAMINO → RETIRADO` (válido para pedidos legacy)
  - `EN_CAMINO → ENTREGADO` (válido para pedidos legacy)
  - `ENTREGADO → *` rechazado (estado terminal)
  - Cualquier otra transición rechazada con 409 (`OrderDomainException`).
  - `isClaimable()`: pedido sin repartidor asignado (`deliveryId == null`) y estado no cerrado (D3).
- `application/port/outbound/OrderRepositoryPort.java` — agregar `findAvailableForDelivery(UUID restaurantId, int limit)`. Cambiar firma de `claimOrders` a `claimOrders(List<UUID> orderIds, UUID deliveryId)` sin parámetro de estado (D4). Cambiar `countActiveOrdersByDelivery` para que también cuente `LISTO_PARA_RETIRO` (D3). Mantener `findByRestaurantAndStatusAndCreatedAtBetween(...)`.
- `openapi.yaml` — actualizado con los nuevos endpoints y contratos.
- `ClaimDeliveryOrdersUseCase.java` — el claim solo persiste `deliveryId` y no muta el estado; ya no llama a `deliveryPort.updateRouteStatus` (D4, D17). Tests confirman que `Order.status` queda en `LISTO_PARA_RETIRO` post-claim.
- **Corrección del contrato C-3:** `CatalogHttpClientAdapter.findRestaurantIdByUserId` deserializa un objeto, no una lista (acuerdo con Javier) (D16).

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

**`PR-gateway-1`** (profile-and-delivery) — **sin rutas nuevas**: el prefijo existente `/api/orders` ya cubre `/api/orders/available-for-delivery`. Queda como tarea de verificación (smoke test) para confirmar ruteo y reenvío de `Authorization`.

**`PR-gateway-2`** (store-flow) — **incluye código nuevo** además de YAML (la ruta de `sales-summary` ya está cubierta por el prefijo `/api/orders`):

- Registrar parser multipart (`@fastify/multipart` o equivalente) y aumentar `bodyLimit` a ≥ 6MB para soportar imagen 5MB + overhead.
- Modificar `engine.ts` (o nuevo módulo) para **passthrough de bodies multipart** sin `JSON.stringify` — preservar `Content-Type` con boundary, `Content-Length`/`Transfer-Encoding` correctos.
- Registrar rutas:
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
- **`orders.status` admite `EN_CAMINO`** (legacy) — después de PR-orders-claim no se asignan nuevos pedidos a `EN_CAMINO` directamente. El flujo nuevo es `LISTO_PARA_RETIRO → RETIRADO` (pickup confirmado) → `ENTREGADO`. **Los pedidos legacy que ya están en `EN_CAMINO` se mantienen en la BD**; ningún código los transiciona automáticamente a `RETIRADO`. Decisión: el ciclo de vida legacy queda congelado; pedidos en `EN_CAMINO` eventualmente pasan a `RETIRADO` o `ENTREGADO` por flujo normal.
- **Definición de pedido "tomado"**: un pedido tomado sigue en `LISTO_PARA_RETIRO` con `delivery_id` asignado; "tomado" se determina por `delivery_id != null`, no por el estado.

**Confirmación de migraciones:** Sin migraciones. El índice de métricas no se agregó: no se midió en esta iteración (ver §11). Se mantiene el default: cero migraciones.

---

## 6. Estrategia de implementación — dos OpenSpec changes paralelos

Cada OpenSpec change es **autónomamente testeable y deployable**. Los dos cambios NO se pisan entre sí. La subdivisión interna es por servicio (un PR por dueño de servicio) para maximizar paralelismo.

### 6.1 OpenSpec change #1 — `profile-and-delivery`

| PR | Servicio | Contenido | Estado / Bloquea |
|---|---|---|---|
| `PR-auth` | auth-service | `PUT /auth/profile` con use case + DTO + controller + tests | Pendiente |
| `PR-orders-jwt-roles` | orders-service | **Fix bloqueante.** Poblar authorities desde claim `roles` del JWT en `JwtValidationFilter` + `requireCurrentRoles()`/`hasRole()` en `CurrentUserResolver` + enum `Role` en `domain/` + tests. Detalle completo en §4.3.0 | ✅ **Implementado — rama `feat/orders-jwt-roles` (commit `f739138`)** |
| `PR-orders-status-authz` | orders-service | Matriz rol→transición-permitida en `PUT /api/orders/{id}/status` + validación de asignación a Repartidor y dueño Restaurante + tests | ✅ **Implementado — rama `feat/orders-jwt-roles` (commit `ef2e32a`)** |
| `PR-orders-available` | orders-service | `GET /api/orders/available-for-delivery` (use case + controller + tests) sin pedidos tomados | ✅ **Implementado — rama `feat/orders-jwt-roles` (commit `4efe151`)** |
| `PR-orders-claim` | orders-service | Quitar mutación de status a `EN_CAMINO` en `ClaimDeliveryOrdersUseCase.execute()`. Asignar `deliveryId` sin tocar status. `Order.assignDelivery()` eliminado. Requiere `DELIVERY_CLAIM_DELEGATE_TO_ORDERS=true` en FloCI | ✅ **Implementado — rama `feat/orders-jwt-roles` (commit `8605b8e`)** |
| `PR-gateway-1` | gateway | Verificación de rutas de orders (prefijo `/api/orders` existente cubre el ruteo) + reenvío de `Authorization` | Tarea de verificación pendiente |

**Trabajo en paralelo:** los 4 PRs de servicio pueden arrancar en paralelo (archivos disjuntos).

### 6.2 OpenSpec change #2 — `store-flow`

| PR | Servicio | Contenido | Estado / Bloquea |
|---|---|---|---|
| `PR-catalog-image` | catalog-service | S3/MinIO client config + variables de entorno + `POST /api/catalog/my/products/image` + tests | `PR-catalog-products` |
| `PR-catalog-products` | catalog-service | `/api/catalog/my/products` CRUD (POST/GET/PUT/DELETE) con ownership derivado del JWT + tests | `PR-gateway-2` |
| `PR-orders-metrics` | orders-service | `GET /api/orders/restaurants/{id}/sales-summary` con agregaciones (subtotal productos sin despacho, ticket medio, top 5) + `SalesSummaryController` + tests | ✅ **Implementado — rama `feat/orders-jwt-roles` (commit `54713f0`)** |
| `PR-gateway-2` | gateway | Rutas nuevas para catalog (`/api/catalog/my/*`, `/api/catalog/my/products/image`) + soporte multipart (orders ya cubierto) | último |

**Trabajo en paralelo:** `catalog-image` arranca primero (es prerrequisito técnico); `orders-metrics` arranca en paralelo desde el inicio.

### 6.3 Línea de tiempo ideal

```
Change #1:
  PR-orders-jwt-roles ─►                                          (PR previo, bloqueante)
  PR-auth             ─────────►
  PR-orders-1a        ─────────►                                  (espera PR-orders-jwt-roles)
  PR-orders-1b        ─────────►                                  (espera PR-orders-jwt-roles)
  PR-delivery         ─────────►
  PR-gateway-1        ────────────────────►

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
                 → JwtValidationFilter valida JWT contra Auth (HTTP /auth/validate)
                    y extrae userId del `sub` + roles del claim `roles` (ver §4.3.0)
                 → OrderController valida rol Repartidor con hasRole(Role.REPARTIDOR) (403 si no)
                 → ListAvailableOrdersUseCase(restaurantId, limit)
                    ├─ OrderRepositoryPort.findAvailableForDelivery(
                    │      restaurantId, limit) (LISTO_PARA_RETIRO y sin repartidor asignado, FIFO)
                    └─ OrderEnricher.enrich(...) (datos de cliente)
                 → 200 [OrderListResponse]
```

### 7.3 `PUT /api/orders/{id}/status`
```
Flutter → Gateway → orders-service
                 → JwtValidationFilter valida JWT contra Auth (HTTP /auth/validate)
                    y extrae userId del `sub` + roles del claim `roles` (ver §4.3.0)
                 → UpdateOrderStatusUseCase(orderId, newStatus, currentRoles, currentUserId)
                    ├─ OrderRepositoryPort.findById(orderId)
                    ├─ RoleTransitionPolicy: ¿algún rol del usuario puede fijar el nuevo estado? (403 si no)
                    ├─ Si el permiso viene de Restaurante: order.restaurantId == catalogPort.findRestaurantIdByUserId(userId) (403 si no)
                    ├─ Si el permiso viene de Repartidor:  order.deliveryId == deliveryPort.findDeliveryIdByUserId(userId) (403 si no)
                    ├─ order.validateStatusTransition(newStatus) (409 si el estado actual no lo permite)
                    └─ OrderRepositoryPort.save(order)
                 → 200 OK
```

### 7.4 `GET /api/orders/restaurants/{restaurantId}/sales-summary`
```
Flutter → Gateway → orders-service
                 → JwtValidationFilter valida JWT contra Auth y extrae userId + roles (ver §4.3.0)
                 → SalesSummaryController valida rol Restaurante con hasRole(Role.RESTAURANTE) (403 si no)
                 → GetRestaurantSalesSummaryUseCase(restaurantId, range, currentUserId)
                    ├─ Validar que currentUserId es dueño de restaurantId vía
                    │   catalogPort.findRestaurantIdByUserId(currentUserId) (403 si no)
                    ├─ Calcular rango temporal (from/to según day/week/month vía Clock inyectable)
                    ├─ OrderRepositoryPort.findByRestaurantAndStatusAndCreatedAtBetween(...)
                    ├─ Agregaciones (D13):
                    │   ├─ totalOrders = cantidad de pedidos ENTREGADO creados en el rango
                    │   ├─ totalRevenue = suma de subtotales de productos (sin despacho)
                    │   ├─ avgTicket = totalRevenue ÷ totalOrders, redondeado a pesos (HALF_UP); 0 si vacío
                    │   └─ topProducts = hasta 5 productos por cantidad (desempate por mayor ingreso)
                    └─ Construir SalesSummaryResponse
                 → 200 SalesSummaryResponse (dentro de ApiResponse<T>)
```

### 7.5 `/api/catalog/my/products` (CRUD)
```
Flutter → Gateway → catalog-service
                 → Spring Security + JWT RS256 contra JWKS de Auth valida el JWT y mapea
                    el claim `roles` a authorities (SecurityConfig; §4.4)
                 → MyStoreProductController
                    ├─ Verificar authority `ROLE_Restaurante` (403 si no)
                    ├─ RestaurantOwnershipResolver.resolve(userId) [uso interno, NO HTTP self-call]
                    │   └─ GetRestaurantByUserIdUseCase.execute(userId) — local, JPA, sin cache
                    │      (ver §FR-2 de openspec/changes/store-flow/spec.md y §4.4 del plan)
                    ├─ Validar ownership (403 si producto.restaurantId != resolved)
                    ├─ CRUD JPA normal
                    └─ 200/201/204
```

### 7.6 `POST /api/catalog/my/products/image`
```
Flutter → Gateway → catalog-service
                 → Spring Security + JWT RS256 valida el JWT (rol Restaurante; §4.4)
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
| **400** | Body inválido, MIME no permitido, tamaño excedido; parámetro obligatorio faltante o no numérico; `range` inválido; `limit` fuera de 1–50 |
| **401** | JWT ausente o expirado |
| **403** | Rol no autorizado / IDOR (repartidor intenta cambiar pedido no asignado a él; tienda consulta ventas o edita producto de otro restaurante) |
| **404** | Recurso no existe |
| **409** | Transición de estado inválida (ej. Repartidor intenta `ENTREGADO` sobre un pedido en `LISTO_PARA_RETIRO`) |
| **413** | Payload > límite (imagen > 5MB) |
| **502** | Dependencia externa caída (S3/MinIO) |
| **503** | Servicio abajo (lo emite gateway o fallo externo de Catalog) |

Todos en formato `ApiError` de `shared-observability` (nota: `orders-service` usa su propio `ErrorResponse` manteniendo la misma forma `{status, error, message}`).

---

## 9. Estrategia de testing

### Unit tests (sin Docker, corren en CI cada PR)
- `JwtValidationFilterTest` (orders) — matriz: sin JWT, JWT sin claim `roles`, JWT con `["Restaurante"]`, JWT con `["Cliente", "Repartidor"]`, JWT con `roles` mal formado (string en vez de lista). Verifica que `Authentication.getAuthorities()` contiene `ROLE_<rol>` por cada elemento del claim (detalle en §4.3.0).
- `CurrentUserResolverTest` (orders) — `requireCurrentRoles()` con cada valor del enum `Role`, con authority ausente (`AccessDeniedException`), y con authority cuyo sufijo no matchea el enum (`AccessDeniedException("Rol desconocido: <x>")`).
- `RoleTransitionPolicyTest` (orders) — política de transiciones permitidas por rol.
- `UpdateUserProfileUseCaseTest` (auth)
- `ListAvailableOrdersUseCaseTest` (orders) — verificación del listado FIFO excluyendo pedidos ya asignados.
- `UpdateOrderStatusUseCaseTest` (orders) — validación con política de roles, ownership restaurante y asignación repartidor.
- `GetRestaurantSalesSummaryUseCaseTest` (orders) — agregaciones de ventas, ticket medio, top productos y control de ownership.
- `SalesSummaryControllerTest` (orders) — validación de endpoints y respuestas.
- `OwnerProductUseCasesTest` x 4 CRUD (catalog)
- `UploadProductImageUseCaseTest` con stub de S3 (catalog)
- `ClaimDeliveryOrdersUseCaseTest` ajustado: verificar que **no** se modifica `Order.status` ni ruta a `EN_CAMINO` post-claim y no sincroniza ruta (orders)
- `RestaurantOwnershipResolverTest` con stub HTTP (catalog) *(en orders no aplica: se reutiliza `CatalogPort`)*

### Integration tests (test containers, CI los corre)
> **Convención de nomenclatura en orders-service:** en orders-service los tests de integración se nombran `*Test` o `*IntegrationTest` (no `*IT.java` porque Maven no tiene Failsafe configurado; `*IT` no se ejecutaría) (D2).

- `AuthControllerIT` — `PUT /auth/profile` con perfil válido, email duplicado, JWT inválido.
- `OrderControllerStatusAuthzTest` (orders, antes `OrderControllerIT`) — matriz 2D (rol × transición y ownership) para `PUT /api/orders/{id}/status`.
- `AvailableDeliveryOrdersControllerTest` y `JpaOrderRepositoryAdapterTest` (orders, antes `AvailableDeliveryOrdersIT`) — `GET /api/orders/available-for-delivery` filtrando correctamente pedidos listos sin repartidor, orden FIFO y límites contra Postgres real.
- `SalesSummaryIntegrationTest` y `SalesSummaryControllerTest` (orders, antes `RestaurantMetricsIT`) — agregaciones de métricas con Postgres real, validación de ownership.
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
| 1 | El PR de claim cambia comportamiento — clientes viejos de Flutter que asumen `EN_CAMINO` post-claim pueden romperse | **Mitigado** — informado a Javier (Flutter); la app transiciona manualmente a `RETIRADO` y luego `ENTREGADO`. Implementación a cargo de Flutter para cuando todos los servicios cumplan el plan (ver sección 6 del informe) |
| 2 | ~~Cache `userId → restaurantId` en catalog-service puede quedar stale~~ **Eliminado**: el plan corregido usa `GetRestaurantByUserIdUseCase` local, sin cache HTTP self-call | N/A |
| 3 | S3/MinIO en Floci **NO está aprovisionado** para catalog (`infra/floci/INFRASTRUCTURE.md` marca como `not used`). No hay bucket, vars S3, secretos ni task definition | Antes de PR-catalog-image: crear bucket S3 en Floci, endpoint accesible desde el contenedor, credenciales/rol, política de lectura, CORS, vars en `env.shared.template`, task definition ECS y config local |
| 4 | **Build integrado del monorepo está roto** para catalog: `services/build.gradle.kts` fija Spring Boot 3.3.5, `services/catalog-service/build.gradle.kts` declara 3.5.16. `services/gradlew.bat :catalog-service:test` FAIL por conflicto | Tarea previa: alinear versión de Spring Boot O retirar catalog del build raíz. Agregar `catalog-service-ci.yml` que ejecute tests autónomos |
| 5 | **Catalog no tiene Spring Security**. Plan asume `requestMatchers` con `Restaurante`, pero (a) dependencia no está, (b) `SecurityConfig` no existe, (c) gateway ya reenvía `Authorization: Bearer <jwt>` con el claim `roles` (validado contra `JwtTokenService` de Auth y `middleware/jwt-auth/plugin.ts` del gateway), pero el backend debe leerlo. Detalle del fix en §4.3.0 | Agregar `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`. Implementar `SecurityConfig` que valida JWT RS256 contra JWKS de Auth y mapea `roles` a authorities con prefijo `ROLE_`. Convención de roles ya unificada: `Cliente`/`Restaurante`/`Repartidor` (§4.3.0) |
| 6 | **Gateway no soporta multipart** para `/api/catalog/my/products/image`. `engine.ts` serializa con `JSON.stringify`, no hay `@fastify/multipart` en deps, bodyLimit insuficiente | PR-gateway-2 debe incluir código real: parser multipart, bodyLimit ≥ 6MB, passthrough raw stream, preservar `Content-Type` con boundary |
| 7 | **Soft delete no oculta del catálogo público**. `ListProductsUseCase` usa `findAll`/`findByCategoryId`/`findByRestaurantId` sin filtrar `is_available`. Si se implementa el DELETE sin esto, productos desactivados siguen visibles | Agregar queries `...AndIsAvailableTrue` y métodos separados `findAllAvailable()`, `findByRestaurantIdAndAvailableTrue(...)`. Usarlos en endpoints públicos. `/api/catalog/my/*` puede usar los métodos sin filtro para ver desactivados |
| 8 | **Contrato de errores inconsistente**. Plan exige `ApiError` de shared-observability, pero catalog usa `ErrorResponse` propio y `RestExceptionHandler` no cubre 401/403/413/502 | Decisión: adoptar `ApiError` para consistencia con auth/orders/delivery (recomendado), o mantener `ErrorResponse` y documentar la divergencia. Agregar handlers específicos |
| 9 | (RESUELTO por `PR-orders-jwt-roles`, commit `f739138`) Autorización por rol dependía de que orders-service leyera el claim `roles` del JWT. El filtro actual los descartaba | **Resuelto** — commit `f739138` |
| 10 | Métricas con `LISTO_PARA_RETIRO` muy alto en alguna tienda → query lenta | No medido; el índice no se agregó (se mantiene default: cero migraciones) |
| 11 | Tests de integración contra S3 real son flaky | Usar LocalStack o stub in-memory; documentar |
| 12 | El PR-gateway-1 depende de que PR-orders-status-authz y PR-orders-available estén mergeados. Si un dev lo mergea antes, las rutas devuelven 404 | PR-gateway-1 reformulado como verificación (las rutas de orders ya están cubiertas por `/api/orders`) |
| 13 | catalog-image y catalog-products se mergean en paralelo y ambos tocan `build.gradle.kts` (Spring Security + AWS SDK) y `SecurityConfig` → conflicto | catalog-products depende técnicamente de catalog-image (comparten `build.gradle.kts`, `SecurityConfig`, `application.yml`); el dev de catalog los mergea secuencialmente en su orden interno |
| 14 | **`Order.validateStatusTransition()` actual está incompleto** (solo rechaza `ENTREGADO → *`) | **Resuelto** — commit `ef2e32a`: matriz completa `from × to` con `RoleTransitionPolicy` |
| 15 | **IDOR en `updateOrderStatus` para rol `Restaurante` y `Repartidor`** | **Resuelto** — commit `ef2e32a`: validación de dueño de restaurante vía `catalogPort.findRestaurantIdByUserId` y asignación de repartidor vía `deliveryPort.findDeliveryIdByUserId` |
| 16 | **`openapi.yaml` queda desactualizado** | Resuelto en commits de orders-service con actualización de openapi |
| 17 | **`EN_CAMINO` queda como estado legacy** | **Resuelto** — Repartidor puede llevar `EN_CAMINO → RETIRADO` y `EN_CAMINO → ENTREGADO`; ningún rol puede llevar a `EN_CAMINO` |
| 18 | **Bug bloqueante de authorities vacías en JWT** | **Resuelto** — commit `f739138` |
| 19 | El claim de la app pasa por delivery-service y solo llega a Orders si `DELIVERY_CLAIM_DELEGATE_TO_ORDERS=true`. Sin eso, Orders no conoce el repartidor asignado: el repartidor recibe 403 al marcar Retirado y el pedido sigue disponible | **Mitigado** — activación aprobada en FloCI (`infra/floci/task-definitions/delivery-service.dev.json`), a desplegar junto con los cambios de Orders (ver sección 5 del informe) |
| 20 | Testcontainers 1.19.7 (orders-service) no funciona con Docker Engine 29: los tests de base de datos fallan en equipos con Docker Desktop reciente | **Mitigación**: subir la versión en el `pom.xml` de orders-service (o temporalmente configurar `~/.docker-java.properties` con `api.version=1.44`) |

---

## 12. Próximos pasos

Esta sesión **solo planeó**. Cuando se apruebe este doc:

0. **(Hecho — commit `f739138`) Implementar `PR-orders-jwt-roles`** — fix del bug de lectura de roles del JWT en `orders-service/JwtValidationFilter.java:86-91`.
1. **Crear los dos OpenSpec changes** (`openspec/changes/profile-and-delivery/` y `openspec/changes/store-flow/`) con `proposal.md`, `tasks.md`, `spec.md` y `design.md`.
2. **Asignar los PRs** a los dueños de cada servicio según la tabla §6.
3. **(Hecho en Orders) Coordinar con el dev de Flutter** el cambio de comportamiento del claim (informado a Javier; ver sección 6 del informe).
4. **Configurar el bucket S3/MinIO** en Floci antes del PR-catalog-image (riesgo #3).
5. **Mergear en orden** los PRs internos de cada change.
6. **Activar `DELIVERY_CLAIM_DELEGATE_TO_ORDERS=true` en FloCI** (`infra/floci/task-definitions/delivery-service.dev.json`) al desplegar delivery-service / orders-service.
7. **Ejecutar pruebas de verificación del gateway** (G1–G8 del informe: validación de rutas y reenvío de `Authorization`).
8. **Abrir PR de Orders** cuando todos los servicios estén listos y avisar a Nicolás al mergear (el servidor no se actualiza solo).
9. **Prueba de punta a punta en FloCI**: tomar pedido → Retirado → Entregado → resumen de ventas.

### 12.1 Datos de prueba de referencia (seeds de development)
- `cliente@demo.cl` (usuario 1, rol `Cliente`)
- `restaurante@demo.cl` (usuario 2, rol `Restaurante`, dueño del restaurante 1 "Urban Burger Demo")
- `repartidor@demo.cl` (usuario 3, rol `Repartidor`)
- `admin@demo.cl` (usuario 4, usuario multirol con los 3 roles: `Cliente`, `Restaurante`, `Repartidor`, dueño del restaurante 2 "Flash Restaurant Demo")

---

**Sesión de brainstorming cerrada.** Documento actualizado conforme a la implementación y acuerdos del informe de Orders.