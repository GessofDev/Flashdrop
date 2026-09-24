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
| `delivery-service` | Ajustar `claim` para no mutar el estado del pedido |
| `orders-service` | Listado de pedidos disponibles, autorización por rol en cambio de estado, métricas de ventas |
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

**Sin archivos nuevos.**

**Archivos modificados:**
- `application/usecase/ClaimDeliveryOrdersUseCaseImpl.java` — eliminar la llamada que mutaba `Order.status`.
- `domain/model/Order.java` (o donde se defina `assignDelivery`) — ajustar para que `assignDelivery` solo persista `deliveryId`, sin tocar `status`. **Caveat**: la clase `Order` formalmente vive en `orders-service`; `Order.assignDelivery` parece estar duplicado o referenciado desde delivery-service. Verificar en implementación antes de mergear y coordinar con el dev de orders si hay cambios en el `Order` compartido.
- `application/port/outbound/OrderServicePort.java` (o equivalente) — ajustar el método `claim` para reflejar que ya no hay mutación de estado.

**Sin migración de DB.**

### 4.3 `orders-service`

**Nuevos archivos:**
- `application/usecase/ListAvailableOrdersUseCase.java`
- `application/usecase/GetRestaurantSalesSummaryUseCase.java`
- `application/dto/SalesSummaryResponse.java`
- `infrastructure/api/AvailableDeliveryOrdersController.java` (o agregar a `OrderController`)
- `infrastructure/api/SalesSummaryController.java` (o agregar a `OrderController`)
- `domain/exception/InvalidStatusTransitionException.java` (si no existe)
- `application/port/outbound/RestaurantMetricsRepository.java` (si se prefiere segregar)

**Archivos modificados:**
- `infrastructure/api/OrderController.java` — agregar authz por rol en `PUT /api/orders/{id}/status`. Cargar matriz rol → transiciones permitidas.
- `application/usecase/UpdateOrderStatusUseCase.java` — incorporar `currentUserRole` y validar contra la matriz. Devolver `AccessDeniedException` si rol no autorizado, `OrderDomainException` si transición inválida.
- `domain/model/Order.java` — revisar `validateStatusTransition()` y `isClaimable()` para reflejar que `assignDelivery` ya no cambia estado.
- `application/usecase/ListOrdersUseCase.java` (posible) — si se decide reutilizar con un parámetro `restaurantId` en vez de crear `ListAvailableOrdersUseCase`.

**Sin migración de DB.**

### 4.4 `catalog-service`

**Nuevos archivos:**
- `infrastructure/adapter/inbound/rest/MyStoreProductController.java`
- `infrastructure/adapter/inbound/rest/MyStoreImageController.java`
- `application/usecase/OwnerCreateProductUseCase.java`
- `application/usecase/OwnerListProductsUseCase.java`
- `application/usecase/OwnerUpdateProductUseCase.java`
- `application/usecase/OwnerDeleteProductUseCase.java`
- `application/usecase/UploadProductImageUseCase.java`
- `application/port/outbound/RestaurantOwnershipResolver.java` (interfaz) + implementación HTTP que llama a `/api/internal/restaurants?userId={userId}` con cache en memoria (TTL 60s).
- `infrastructure/adapter/outbound/storage/S3ProductImageStorage.java`
- `infrastructure/config/StorageConfig.java`
- `application/dto/UploadImageResponse.java`
- `infrastructure/adapter/inbound/rest/dto/OwnerCreateProductRequest.java`
- `infrastructure/adapter/inbound/rest/dto/OwnerUpdateProductRequest.java`

**Archivos modificados:**
- `infrastructure/config/SecurityConfig.java` (o equivalente) — garantizar que `/api/catalog/my/**` requiere JWT y rol `store_owner`.
- `application/usecase/CreateProductUseCase.java` (o el que esté hoy) — el controller `MyStoreProductController` NO reutiliza este; crea su propio use case para mantener separación clara.

**Sin migración de DB.** `product.image` ya es `String`.

### 4.5 `gateway`

**Sin código nuevo.** Modificar `gateway/config/*.yaml` (o equivalente) para registrar:

| Path público | Upstream |
|---|---|
| `/api/orders/available-for-delivery` | `orders-service:8083` |
| `/api/orders/restaurants/{id}/sales-summary` | `orders-service:8083` |
| `/api/catalog/my/*` | `catalog-service:8082` |

(Las rutas existentes `/api/auth/*`, `/api/orders/*`, `/api/catalog/*`, `/api/delivery/*` siguen iguales.)

---

## 5. Datos y migraciones

**No se agregan migraciones Flyway.** Todas las tablas y columnas necesarias ya existen:

- `users` (auth) — `name`, `last_name`, `phone`, `photo` ya están en el esquema V1.
- `products` (catalog) — `image` ya es `TEXT`.
- `orders` (orders) — `status`, `restaurant_id`, `created_at` ya indexados o indexables.
- `delivery_routes` (delivery) — `order_id`, `delivery_person_id` ya disponibles.
- **`auth-service.users.updated_at`** (V1 línea 24) — la columna existe desde el alta con `default now()`, pero no está mapeada en `UserEntity` ni tiene trigger. **El fix es en código Java (`@PreUpdate` en la entidad), no en SQL.** Cumple la promesa de "sin migración".

**Índice sugerido (no migración, decisión de PR-orders-metrics):** confirmar `CREATE INDEX IF NOT EXISTS idx_orders_restaurant_status_created ON orders(restaurant_id, status, created_at DESC)` en la primera corrida de PR-orders-metrics si el EXPLAIN muestra lag. Es trivial agregar como Flyway al PR que lo necesite.

---

## 6. Estrategia de implementación — dos OpenSpec changes paralelos

Cada OpenSpec change es **autónomamente testeable y deployable**. Los dos cambios NO se pisan entre sí. La subdivisión interna es por servicio (un PR por dueño de servicio) para maximizar paralelismo.

### 6.1 OpenSpec change #1 — `profile-and-delivery`

| PR | Servicio | Contenido | Bloquea |
|---|---|---|---|
| `PR-auth` | auth-service | `PUT /auth/profile` con use case + DTO + controller + tests | — |
| `PR-orders-status-authz` | orders-service | Matriz rol→transición-permitida en `PUT /api/orders/{id}/status` + tests de la matriz | `PR-gateway-1` |
| `PR-orders-available` | orders-service | `GET /api/orders/available-for-delivery` (use case + controller + tests) | `PR-gateway-1` |
| `PR-delivery` | delivery-service | Quitar mutación de status en `ClaimDeliveryOrdersUseCaseImpl` + ajustar `Order.assignDelivery()` + tests | — |
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
- `ClaimDeliveryOrdersUseCaseImplTest` ajustado: verificar que **no** se modifica `Order.status` post-claim (delivery)
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
| 2 | Cache `userId → restaurantId` en catalog-service puede quedar stale | TTL 60s en memoria; suficiente para MVP |
| 3 | S3/MinIO en Floci requiere CORS y bucket público (o URLs firmadas) para que Flutter muestre imágenes | Lo decide el PR de catalog-image; documentar en `env.shared.template` y `infra/coolify/` |
| 4 | Autorización por rol depende de que el JWT traiga `roles[]`. Si no está, la authz falla cerrado | Verificar que `RegisterUserUseCase` asigne roles correctamente; tests cubren "JWT sin roles" |
| 5 | Métricas con `LISTO_PARA_RETIRO` muy alto en alguna tienda → query lenta | Evaluar índice `orders(restaurant_id, status, created_at)` en PR-orders-metrics |
| 6 | Tests de integración contra S3 real son flaky | Usar LocalStack o stub in-memory; documentar |
| 7 | El PR-gateway-1 depende de que PR-orders-status-authz y PR-orders-available estén mergeados. Si un dev lo mergea antes, las rutas devuelven 404 | PR-gateway-1 va al final, después de que los otros estén mergeados a `main` (o se acepta el orden de merge como dependencia natural) |
| 8 | Si catalog-image y catalog-products se mergean en paralelo y ambos tocan `application.yml` o `SecurityConfig`, hay conflicto | catalog-products depende técnicamente de catalog-image (comparte configuración de seguridad); el dev de catalog los mergea secuencialmente en su orden interno |

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