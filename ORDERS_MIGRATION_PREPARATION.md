# ORDERS_MIGRATION_PREPARATION — Análisis y Preparación de la migración de Orders Service

> Documento generado como **PROMPT 1** del plan de migración. Es la fuente de verdad para los prompts 2 (adapters SQL→HTTP), 3 (BD propia + endpoint interno) y 4 (testing).
>
> **Reglas aplicadas:** no se modificó ningún archivo; no se crearon controllers, adapters ni configuraciones; solo inspección del código disponible.
>
> **Clasificaciones usadas:** `CONFIRMADO` (evidencia directa en el código), `INFERIDO` (se deduce de referencias, no verificable por completo), `NO VERIFICABLE` (requiere material que no está en este proyecto). Contratos no verificables marcados como `PENDIENTE DE CONFIRMACIÓN`.

---

## 1. Estado actual

**CONFIRMADO.** El proyecto real de Orders Service es un microservicio Spring Boot que vive en:

```
juniors/junior-2-orders/orders-service/
```

Stack (evidencia: `orders-service/pom.xml`, `orders-service/README.md`):

| Componente | Valor |
|---|---|
| Framework | Spring Boot 3.2.5 (`spring-boot-starter-web`, `-security`, `-validation`, `-actuator`, `-amqp`) |
| Lenguaje | Java 21 |
| Build | Maven (group `cl.flashdrop`, artifact `orders-service`, versión 1.0.0) |
| Puerto | 8083 (`application.properties`) |
| Persistencia | Supabase vía **REST API (PostgREST)** con `RestClient` de Spring. NO usa JDBC/JPA/Flyway en el perfil `supabase` |
| Mensajería | RabbitMQ (opcional; si falla, solo loguea warning) |
| Testing declared | JUnit 5 + Mockito; Testcontainers en pom pero **sin usar** |
| Perfil activo | `supabase` → excluye `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `FlywayAutoConfiguration` |

Arquitectura hexagonal (Ports & Adapters) implementada en estos paquetes `src/main/java/cl/flashdrop/orders/`:

- `application/` → `command/`, `dto/`, `usecase/`
- `domain/` → `model/`, `port/`, `exception/`
- `infrastructure/` → `api/` (controllers + DTOs), `exception/`, `messaging/`, `persistence/dto/`, `adapter/outbound/persistence/supabase/`
- `config/` → `SupabaseRestClientConfig`, `SecurityConfig`, `JwtValidationFilter`, `CorsConfig`, `RabbitMQConfig`

**Hallazgo relevante:** el README y `openapi.yaml` documentan endpoints `/api/delivery/routes` y `/api/delivery/claim`, pero **no existe ningún controller que los exponga** (ver sección 2). Los casos de uso `ClaimDeliveryOrdersUseCase`, `ListDeliveryRoutesUseCase` y el DTO `ClaimDeliveryRequest` son **código no alcanzable** (nada los referencia salvo a sí mismos).

El repositorio también contiene:
- Monolito Node.js original (raíz) — origen de los contratos actuales públicos (`routes/appRoutes.js`, `controllers/ordersController.js`). Fuera de alcance, solo contexto.
- `services/delivery-service/` — Delivery Service (Spring Boot), parte del ecosistema.
- `juniors/junior-1-auth/auth-service/` — Auth Service (Spring Boot).
- `juniors/junior-3-catalog/` — Catalog Service (Spring Boot).
- `gateway/` — API Gateway (Node/Fastify), fuera del alcance de esta migración.

---

## 2. Arquitectura actual

Diagrama textual basado en el código real:

```
                    HTTP público (gateway / clientes)
                              │
                         8083: Orders Service
                              │
      ┌───────────────────────┼───────────────────────────┐
      │                       │                           │
 OrderController   HealthController            (NO existe DeliveryController a pesar
 /api/orders...    /health                         de openapi.yaml y README)
      │                       │
      └───────────────┬───────┘
                      │
        Use Cases (application/usecase/)
   CreateOrder · ListOrders · GetOrderDetail · UpdateOrderStatus
   ClaimDeliveryOrders (HUÉRFANO) · ListDeliveryRoutes (HUÉRFANO)
                      │
     ┌────────────────┼─────────────────┬─────────────────┐
     │                │                 │                 │
 OrderRepositoryPort  CatalogPort      DeliveryPort    EventPublisherPort
     │                │                 │                 │
     ▼                ▼                 ▼                 ▼
 SupabaseRestOrder  SupabaseRest      SupabaseRest    RabbitMQEventPublisher
 RepositoryAdapter  CatalogAdapter    DeliveryAdapter  (RabbitMQ, opcional)
     │                │                 │
     └────────────────┼─────────────────┘
                      ▼
        Supabase Kong / PostgREST  /rest/v1
        (BD COMPARTIDA con los otros 3 servicios)
```

**Componentes reales y archivos (CONFIRMADO):**

| Capa | Archivos |
|---|---|
| Application | `application/usecase/CreateOrderUseCase.java`, `ListOrdersUseCase.java`, `GetOrderDetailUseCase.java`, `UpdateOrderStatusUseCase.java`, `ClaimDeliveryOrdersUseCase.java` (huérfano), `ListDeliveryRoutesUseCase.java` (huérfano), `application/command/CreateOrderCommand.java`, `application/dto/CreatedOrderResult.java` |
| Domain models | `domain/model/Order.java`, `OrderItem.java`, `OrderStatus.java`, `PaymentMethod.java`, `ProductInfo.java`, `RestaurantInfo.java`, `ClientInfo.java`, `DeliveryInfo.java`, `DeliveryRoute.java` |
| Domain ports | `domain/port/OrderRepositoryPort.java`, `CatalogPort.java`, `DeliveryPort.java`, `EventPublisherPort.java` |
| Domain exception | `domain/exception/OrderDomainException.java` |
| Persistence adapters | `infrastructure/adapter/outbound/persistence/supabase/SupabaseRestOrderRepositoryAdapter.java`, `SupabaseRestCatalogAdapter.java`, `SupabaseRestDeliveryAdapter.java` |
| Persistence DTOs | `infrastructure/persistence/dto/{OrderRow,OrderItemRow,ProductRow,RestaurantRow,ClientRow,UserRow,DeliveryRow,DeliveryRouteRow}.java` |
| REST controllers | `infrastructure/api/OrderController.java`, `HealthController.java` |
| API DTOs | `infrastructure/api/dto/request/{CreateOrderRequest,UpdateOrderStatusRequest,ClaimDeliveryRequest}.java`, `infrastructure/api/dto/response/{ApiResponse,ErrorResponse,OrderListResponse,OrderDetailResponse}.java` |
| Exception handling | `infrastructure/exception/GlobalExceptionHandler.java` |
| Messaging | `infrastructure/messaging/adapter/RabbitMQEventPublisher.java`, `infrastructure/messaging/event/{OrderCreatedEvent,OrderStatusUpdatedEvent}.java` |
| Config | `config/SupabaseRestClientConfig.java`, `SecurityConfig.java`, `JwtValidationFilter.java`, `CorsConfig.java`, `RabbitMQConfig.java` |
| Recursos | `resources/application.properties`, `resources/application-supabase.properties`, `resources/db/migration/V1__init.sql` (dormida), `resources/db/migration_local/V1_1__seed_local_data.sql` (H2, dormida) |
| Tests | `src/test/java/.../application/usecase/CreateOrderUseCaseTest.java`, `.../domain/model/OrderDomainTest.java` |
| Extras | `Dockerfile`, `openapi.yaml`, `.env.example`, `.env.bak` (antiguo, usa `DATABASE_URL`), `.mvn/`, `mvnw`, `mvnw.cmd`, `infrastructure/persistence/adapter/test.txt` (archivo basura `hello`) |

**Notas de arquitectura importantes:**
- Todos los IDs en el dominio son `java.util.UUID`, pero en BD son `bigint` (ver sección 8). Los adapters "envuelven" el Long en UUID con `new UUID(0, rawId)` y lo "desenvuelven" con `extractRawId(...)` (ej. `SupabaseRestCatalogAdapter.java:59-67`, `SupabaseRestOrderRepositoryAdapter.java:274-282`). Es un mecanismo frágil a considerar en los nuevos contratos HTTP.
- El port `DeliveryPort` mezcla dos dominios: consulta la tabla **propia** `client` y las tablas **externas** `users` y `delivery` (`SupabaseRestDeliveryAdapter.java`).
- El `OrderRepositoryPort` mezcla persistencia propia (`orders`, `order_items`) con persistencia ajena (`delivery_routes`) en los métodos `saveRoute`, `updateRouteStatus`, `updateRouteStatusByOrder`, `findAllRoutesWithOrders` (`SupabaseRestOrderRepositoryAdapter.java:145-226`).
- `CreateOrderUseCase` usa `@Transactional` pero el perfil `supabase` no define `PlatformTransactionManager` (no hay DataSource). Funciona como no-transaccional.
- `SecurityConfig` solo exige autenticación para `/api/orders/**`; `/api/delivery/**`, `/health`, `/api/internal/**` (nuevo) **no están contemplados**. La autenticación actual es todo o nada según `JwtValidationFilter`.

---

## 3. Dependencias SQL externas

Acceso cross-domain identificado con evidencia directa (`sed.toSql` del código de los adapters). Todos son queries contra el REST de PostgREST (esto es, acceso directo a tablas de otros dominios vía la BD compartida):

| Dominio externo | Archivo | Método | Tabla/Query (path PostgREST) | Uso actual | Evidencia |
|---|---|---|---|---|---|
| Catalog | `.../SupabaseRestCatalogAdapter.java` | `findProductsByIds` | `/products? id=in.(...)&select=*` | Obtener precio/disponibilidad/restaurantId en creación de pedido | `SupabaseRestCatalogAdapter.java:25-34` |
| Catalog | `.../SupabaseRestCatalogAdapter.java` | `findRestaurantById` | `/restaurant?id=eq.&select=*` | Obtener nombre+dirección del restaurante para la ruta | `:37-46` |
| Catalog | `.../SupabaseRestCatalogAdapter.java` | `findRestaurantIdByUserId` | `/restaurant?user_id=eq.&select=id` | Filtrar pedidos por dueño de restaurante (listar) | `:49-57` |
| Auth | `.../SupabaseRestDeliveryAdapter.java` | `findClientById` | `/users?id=eq.&select=*` (derecha del cliente) | Obtener name/lastName/email/phone del cliente | `:42-65` |
| Auth | `.../SupabaseRestDeliveryAdapter.java` | `findDeliveryById` | `/users?id=eq.&select=*` (derecha del repartidor) | Obtener name/lastName/phone del repartidor | `:79-102` |
| Delivery | `.../SupabaseRestDeliveryAdapter.java` | `findDeliveryIdByUserId` | `/delivery?user_id=eq.&select=id` | Resolver repartidor por userId (claim) | `:68-76` |
| Delivery | `.../SupabaseRestDeliveryAdapter.java` | `findDeliveryById` | `/delivery?id=eq.&select=*` | Obtener vehículo y perfil del repartidor | `:79-86` |
| Delivery | `.../SupabaseRestOrderRepositoryAdapter.java` | `saveRoute` | `POST /delivery_routes` | Crear ruta al crear pedido | `:145-159` |
| Delivery | `.../SupabaseRestOrderRepositoryAdapter.java` | `updateRouteStatus` | `PATCH /delivery_routes?order_id=in.(...)` | Sincronizar estado de rutas en claim | `:162-175` |
| Delivery | `.../SupabaseRestOrderRepositoryAdapter.java` | `updateRouteStatusByOrder` | `PATCH /delivery_routes?order_id=eq.` | Sincronizar estado de ruta al actualizar estado | `:178-190` |
| Delivery | `.../SupabaseRestOrderRepositoryAdapter.java` | `findAllRoutesWithOrders` | `GET /delivery_routes` + `GET /orders` | Listar rutas con pedido asociado | `:193-226` |

**Tablas propias que SÍ consulta Orders (sin cambio de mecanismo):**
- `orders`, `order_items` → `SupabaseRestOrderRepositoryAdapter.java` (todo el CRUD).
- `client` → `SupabaseRestDeliveryAdapter.findClientIdByUserId` / `findClientById` (profile del cliente — tabla propia de Orders según ownership, sección 8).

**Mecanismo objetivo (definido, NO implementado):** `SQL directo → API HTTP`

```
Orders → (RestClient) → Supabase /rest/v1 → tabla externa      (HOY)
Orders → (HTTP client) → servicio dueño → /api/internal/...    (META)
```

---

## 4. Adapters afectados

| Adapter | Port que implementa | Responsabilidad | Dependencias | Qué pertenece a Orders | Qué corresponde a otro dominio | Deberá reemplazarse por HTTP |
|---|---|---|---|---|---|---|
| `SupabaseRestOrderRepositoryAdapter` | `OrderRepositoryPort` | Persistir `orders`/`order_items` y gestionar `delivery_routes` | `RestClient supabaseRestClient` | `orders`, `order_items` (crud completo) y mapping de dominio | Métodos de `delivery_routes` (`saveRoute`, `updateRouteStatus`, `updateRouteStatusByOrder`, `findAllRoutesWithOrders`) | **Parcial**: conservar CRUD de orders/order_items; mover todos los métodos de ruta a un cliente de **Delivery HTTP** |
| `SupabaseRestCatalogAdapter` | `CatalogPort` | Obtener productos y restaurante | `RestClient supabaseRestClient` | Nada | `products` y `restaurant` (100% Catalog) | **Total**: reemplazar por `CatalogHttpClientAdapter` que consuma Catalog |
| `SupabaseRestDeliveryAdapter` | `DeliveryPort` | Resolver clientes, usuarios y repartidores | `RestClient supabaseRestClient` | Consulta a `client` (propia de Orders) | Consultas a `users` (Auth) y `delivery` (Delivery) | **Parcial**: la resolución de cliente puede permanecer (tabla propia); `users`→Auth HTTP y `delivery`→Delivery HTTP |

Observaciones que afectan el diseño de los nuevos adapters:
- `CatalogPort.findProductsByIds` exige devolver `price`, `available`, `name`, `description`, `image`, `restaurantId` por producto. El endpoint de Catalog debe cubrir estos campos.
- `CatalogPort.findRestaurantById` cubre `name` + `address`. `findRestaurantIdByUserId` cubre `restaurant.id` por `user_id` (Catalog aún no tiene este endpoint).
- `DeliveryPort.findClientIdByUserId` además incluye un "fallback demo": si `userId == null` trae el **primer cliente** (`order id.asc limit 1`). Comportamiento a preservar o confirmar (ver sección 14).
- `DeliveryPort.findDeliveryById`/`findClientById` combinan la tabla propia + `users`. En la meta, el nombre/email/phone del cliente debe venir de **Auth**, y del repartidor de **Delivery**.

---

## 5. Información disponible de servicios externos

> **Dato clave del proyecto:** el código fuente de Auth, Catalog y Delivery **SÍ está presente** en este repositorio. De todos modos, **ninguno de los endpoints internos del plan existe todavía** (verificado con grep de `internal` en todos los `*.java` del repo → 0 resultados).

### 5.1 Auth Service — Nicolás

```
Código fuente completo disponible: SI  →  juniors/junior-1-auth/auth-service/ (puerto 8081)
```

Evidencia encontrada (desde el repo, consultada para conocer qué expone):
- `AuthController.java` → `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/validate`, `/auth/profile`.
- `JwksController.java`, `HealthController.java`.
- `application/dto/UserProfile.java` → record `(Long userId, String name, String lastName, String email, String phone, String rut, String photo, List<String> roles)`. **IDs Long.**
- `application.yml` → puerto `8081`, BD propia (`flashdrop_auth`, JPA + Flyway, perfil `supabase` con REST a su Supabase). Ya migró a BD propia.
- Orders **ya consume** Auth vía HTTP para validación de JWT: `JwtValidationFilter.java` → `GET {auth.service.url}/auth/validate` con header `Authorization: Bearer ...` (`config/JwtValidationFilter.java:44-48`).

Contrato HTTP verificable: **PARCIAL**

Información faltante:
- `GET /api/internal/users/{userId}` → **NO EXISTE** (solo existe `/auth/profile` que deriva el userId del token, no del path).
- Confirmar si Auth debe exponer perfil por ID sin token de usuario (para use-case service-to-service).

### 5.2 Catalog Service — Javier

```
Código fuente completo disponible: SI  →  juniors/junior-3-catalog/ (puerto 8082)
```

Evidencia encontrada:
- `ProductController.java` → `POST /catalog/products`, `GET /catalog/products`, `POST /catalog/products/validate` (cuerpo `{ productIds: List<Long> }`, respuesta `ValidateProductsResponse(valid, products[], missingIds[])`).
- `RestaurantController.java` → `GET /catalog/restaurants` (solo listado). **No** hay get-by-id ni por userId.
- `CategoryController.java`, `HealthController.java`.
- DTOs: `ProductResponse(Long id, Long categoryId, Long restaurantId, String name, String description, BigDecimal price, String image, boolean available)` — **incluye todos los campos que Orders necesita** de productos. `RestaurantResponse(Long id, String name, String address, String phone, String image)` (phone/image null en `fromDomain`).
- `GetProductsByIdsUseCase.java` → `findByIds(List<Long>)` (backing del validate).
- `application.yaml` → puerto 8082, perfil `supabase` (REST a su Supabase; BD compartida aún en este punto).

Contrato HTTP verificable: **PARCIAL**

Información faltante (endpoints del plan que NO existen):
- `GET /api/internal/products?ids=...` → **NO EXISTE** (existe `POST /catalog/products/validate`, de respuesta distinta).
- `GET /api/internal/restaurants/{id}` → **NO EXISTE**.
- `GET /api/internal/restaurants?userId=...` → **NO EXISTE**.

### 5.3 Delivery Service — Sebastián

```
Código fuente completo disponible: SI  →  services/delivery-service/ (puerto 8084)
```

Evidencia encontrada:
- `DeliveryController.java` → `POST /api/delivery/claim` (y alias `/delivery/claim`) con body `ClaimDeliveryRequest(deliveryPersonId: Long, orderIds: List<Long> @Size(max=3))`, respuesta `ApiResponse<List<DeliveryPersonResponse>>`.
- `RouteController.java` → `GET /api/delivery/routes` (query `deliveryPersonId`) y `PUT /api/delivery/routes/{routeId}/status` (body `UpdateRouteStatusRequest(status)`).
- DTOs: `DeliveryPersonResponse(Long id, Long userId, String vehicle, Instant createdAt)`, `RouteResponse(Long id, Long orderId, String pickupAddress, String deliveryAddress, Double distanceKm, Integer estimatedMinutes, String status, Instant createdAt, String code)`. **IDs Long.**
- `OrderServiceClientAdapter.java` → Delivery consulta `orders` y `restaurant` **directamente en la BD compartida** (`/orders?id=in.(...)&select=id,client_id,restaurant_id,delivery_id,status,address`). Es decir: Delivery **todavía no** consume `GET /api/internal/orders` de Orders.
- `OrderRow.java` (delivery) → record con campos `id, clientId, restaurantId, deliveryId, status, address, code`. Ojo: Delivery espera un campo `code` en la orden.
- `application.yml`/`application-supabase.yml` → puerto 8084.
- Tests de delivery: varios (usecase, supabase repos, controllers y `OrderServiceClientAdapterTest`).

Contrato HTTP verificable: **PARCIAL**

Información faltante:
- `GET /api/internal/delivery-persons?userId=...` → **NO EXISTE**.
- `POST /api/internal/routes` → **NO EXISTE**.
- `PATCH /api/internal/routes/{orderId}/status` → **NO EXISTE** (Delivery hoy expone `PUT /api/delivery/routes/{routeId}/status` por `routeId`, no por `orderId`, y es `PUT`).

---

## 6. Contratos HTTP conocidos

Únicamente contratos respaldados por evidencia real en este repositorio.

### 6.1 Contra Auth (usado HOY por Orders)

```
Servicio: Auth
Endpoint: /auth/validate
Método: GET
Request: ninguno (header)
Headers: Authorization: Bearer <JWT>
Response: 2xx → token válido; 4xx/5xx → token inválido
Errores: fallo de conexión RestClientException → 401 (manejado en el filter)
Autenticación: sin token previo de Orders (propaga el JWT del cliente)
Fuente de evidencia:
  juniors/junior-2-orders/orders-service/src/main/java/cl/flashdrop/orders/config/JwtValidationFilter.java:44-48
  juniors/junior-1-auth/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/AuthController.java:62-65
```

### 6.2 Contra Catalog (endpoints existentes)

```
Servicio: Catalog
Endpoint: /catalog/products/validate
Método: POST
Request: { "productIds": [<Long>, ...] }
Headers: Content-Type: application/json
Response 200: { "valid": bool, "products": [ {id, categoryId, restaurantId, name, description, price, image, available}, ... ], "missingIds": [<Long>, ...] }
Errores: (no documentados en el controller)
Autenticación: ninguna en el controller
Fuente de evidencia:
  juniors/junior-3-catalog/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/ProductController.java:73-89
  juniors/junior-3-catalog/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/dto/ValidateProductsResponse.java
```

```
Servicio: Catalog
Endpoint: /catalog/restaurants
Método: GET
Response 200: [ { "id": <Long>, "name": <string>, "address": <string>, "phone": null, "image": null }, ... ]
Autenticación: ninguna en el controller
Fuente de evidencia:
  juniors/junior-3-catalog/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/RestaurantController.java:23-30
  juniors/junior-3-catalog/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/dto/RestaurantResponse.java
```

### 6.3 Contra Delivery (endpoints existentes)

```
Servicio: Delivery
Endpoint: /api/delivery/claim  (alias /delivery/claim)
Método: POST
Request: { "deliveryPersonId": <Long>, "orderIds": [<Long>, ...] }  (máx. 3)
Response 201: ApiResponse< List<DeliveryPersonResponse> >
Errores: 400 (validation)
Autenticación: ninguna en el controller
Fuente de evidencia:
  services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/inbound/rest/DeliveryController.java:31-39
  .../application/dto/ClaimDeliveryRequest.java
```

```
Servicio: Delivery
Endpoint: /api/delivery/routes
Método: GET
Query parameters: deliveryPersonId (opcional, ignorado en código)
Response 200: ApiResponse< List<RouteResponse> >
Autenticación: ninguna en el controller
Fuente de evidencia:
  services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/inbound/rest/RouteController.java:37-43
```

```
Servicio: Delivery
Endpoint: /api/delivery/routes/{routeId}/status
Método: PUT
Request: { "status": <string> }
Response 200: ApiResponse< RouteResponse >
Autenticación: ninguna en el controller
Fuente de evidencia:
  services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/inbound/rest/RouteController.java:45-52
```

### 6.4 Contracto del client de Delivery hacia Orders (dirección opuesta)

```
Servicio: Orders (consumido por Delivery)
Endpoint consultado por Delivery hoy: /orders?id=in.(...)&select=id,client_id,restaurant_id,delivery_id,status,address
Método: GET (via Supabase, NO /api/internal/orders)
Campos: id, client_id, restaurant_id, delivery_id, status, address, code
Fuente de evidencia:
  services/delivery-service/src/main/java/com/flashdrop/delivery/infrastructure/adapter/outbound/client/OrderServiceClientAdapter.java:28-51
  .../persistence/supabase/OrderRow.java
```

> Nota: Delivery espera un campo `code` en la lectura de órdenes. El contrato interno propuesto por el plan (`GET /api/internal/orders?ids=...`) **no incluye** `code`. Esto debe confirmarse con el responsable de Delivery (ver sección 14).

### 6.5 Contratos públicos de Orders (para contexto; NO son internos)

Origen: `routes/appRoutes.js` del monolito y `openapi.yaml` de Orders:

- `GET /api/orders` (query `user_id`) · `POST /api/orders` · `GET /api/orders/{id}` · `PUT /api/orders/{id}/status`
- `GET /api/delivery/routes` · `POST /api/delivery/claim` — **documentados pero sin controller en Orders** (brecha)

---

## 7. Contratos HTTP pendientes

Información que debe confirmarse (con el responsable de cada servicio) antes o durante la implementación. **Ninguno de estos endpoints existe actualmente en el código** (`INFERIDO/NO VERIFICABLE`):

| Id | Endpoint | Servicio dueño | Existencia actual | Información faltante |
|---|---|---|---|---|
| C-1 | `GET /api/internal/products?ids=...` | Catalog | **NO EXISTE** | Path/query exacto, formato de `ids` (Long? UUID?), forma de respuesta (array de productos con `price`, `available`, `restaurantId`, `image`, `description`) |
| C-2 | `GET /api/internal/restaurants/{restaurantId}` | Catalog | **NO EXISTE** | Forma respuesta (`id`, `userId`, `name`, `address`); comportamiento 404 |
| C-3 | `GET /api/internal/restaurants?userId=...` | Catalog | **NO EXISTE** | Forma respuesta; criterio "primer restaurante asociado" |
| C-4 | `GET /api/internal/users/{userId}` | Auth | **NO EXISTE** | Forma respuesta (`id`,`name`,`lastName`,`email`,`phone`); nullabilidad; 404; cómo autentica Auth la llamada interna (¿requiere JWT de servicio?) |
| C-5 | `GET /api/internal/delivery-persons?userId=...` | Delivery | **NO EXISTE** | Forma respuesta (`id`, `userId`, `vehicle`); 404 |
| C-6 | `POST /api/internal/routes` | Delivery | **NO EXISTE** | Body exacto (`orderId`, `pickupAddress`, `deliveryAddress`, `distanceKm`, `estimatedMinutes`, `status`); respuesta 201; duplicado → 409 |
| C-7 | `PATCH /api/internal/routes/{orderId}/status` | Delivery | **NO EXISTE** (existe PUT `/api/delivery/routes/{routeId}/status` por `routeId`) | Confirmar si será por `orderId` y método `PATCH`, respuesta y estado |
| C-8 | `GET /api/internal/orders?ids=...` | Orders (este servicio) | **NO EXISTE** | A crear en PROMPT 3. Confirmar con Delivery: ¿incluye `code`? ¿IDs `Long`? |
| C-9 | Header `X-Internal-Api-Key` + rechazo 403 | Todos | **NO EXISTE** en ningún servicio (grep `internal` → 0 hits) | Mecanismo de distribución de la clave (env, secret), valor en cada entorno |
| C-10 | `ErrorResponse` estándar `{status, error, message}` | Todos | Orders usa `{error, message, timestamp}` (`ErrorResponse.java`) **y** envuelve respuestas OK en `ApiResponse` | Confirmar el formato único de errores para endpoints públicos e internos |

---

## 8. Base de datos

### Esquema actual compartido (12 tablas)

Origen verificado: `orders-service/src/main/resources/db/migration/V1__init.sql` (idéntico en `supabase_migration.sql` raíz; `database.sql` contiene solo 7 tablas del monolito original).

| Tabla | FKs (según V1__init.sql) | Clasificación (ownership del plan) |
|---|---|---|
| `users` | — | **PERTENECE A OTRO DOMINIO** (Auth) |
| `login` | `id_users → users` | **PERTENECE A OTRO DOMINIO** (Auth) |
| `roles` | — | **PERTENECE A OTRO DOMINIO** (Auth) |
| `user_has_roles` | `id_user→users`, `id_rol→roles` | **PERTENECE A OTRO DOMINIO** (Auth) |
| `refresh_tokens` | (no está en V1__init.sql; mencionada en plan) | **PERTENECE A OTRO DOMINIO** (Auth) / NO VERIFICABLE en este esquema |
| `categories` | — | **PERTENECE A OTRO DOMINIO** (Catalog) |
| `products` | `category_id→categories`, `restaurant_id→restaurant` | **PERTENECE A OTRO DOMINIO** (Catalog) |
| `restaurant` | `user_id→users` | **PERTENECE A OTRO DOMINIO** (Catalog) |
| `orders` | `client_id→client`, `restaurant_id→restaurant`, `delivery_id→delivery` | **PROPIA DE ORDERS** |
| `order_items` | `order_id→orders`, `product_id→products` | **PROPIA DE ORDERS** |
| `client` | `user_id→users` | **PROPIA DE ORDERS** |
| `delivery` | `user_id→users` | **PERTENECE A OTRO DOMINIO** (Delivery) |
| `delivery_routes` | `order_id→orders` | **PERTENECE A OTRO DOMINIO** (Delivery) |

### Consecuencias para la BD propia de Orders (PROMPT 3)

- La BD de Orders debe contener solo `orders`, `order_items` y `client`.
- **FKs a eliminar en el esquema nuevo** (las entidades viven en otros servicios): `orders.restaurant_id → restaurant`, `orders.delivery_id → delivery`, `order_items.product_id → products`, `client.user_id → users`.
- Los campos cruzados pasan a ser columnas planas `bigint` sin constraint.
- `orders` mantiene `client_id → client` (FK interna válida).
- `delivery_routes` y `products` **no deben existir** en la BD de Orders.
- Migraciones actuales en Orders (`V1__init.sql`, `V1_1__seed_local_data.sql`) están **dormidas**: Flyway deshabilitado en el perfil `supabase` y no existe perfil local. `V1_1` es un seed H2 de TODA la BD compartida (incluye `users`, `restaurant`, `products`, `delivery_routes`), inservible para la BD propia de Orders; habrá que generar un seed solo-Orders en PROMPT 3.

---

## 9. Endpoint interno

### Estado actual

**`GET /api/internal/orders?ids=...` NO EXISTE.**

- No hay controller (`grep internal` en Orders → 0 resultados).
- Safety: `/api/internal/**` no está cubierto por `SecurityConfig` (solo `/api/orders/**` → `authenticated()`).
- No hay DTO interno (`id, clientId, restaurantId, deliveryId, status, address`) ni uso de caso de uso "interno".

### Componentes que deberán crearse en PROMPT 3 (diseño, NO implementado aquí)

| Componente | Diseño propuesto |
|---|---|
| Controller | `infrastructure/api/InternalOrdersController.java` → `GET /api/internal/orders?ids={...}` (respuesta **siempre array**, IDs inexistentes ignorados, array vacío si ninguno existe) |
| Use case | `application/usecase/...` (o método en `OrderRepositoryPort`, p.ej. `findByIds` ligero) reutilizando `OrderRepositoryPort` |
| Port/Repo | Extender `OrderRepositoryPort` con un `findByIds(List<UUID>)` o reutilizar `findByIdsForClaim` (este devuelve `Order` con estado y valida existencia; ojo: no incluye items y no expone `code`) |
| DTO respuesta | record con `id, clientId, restaurantId, deliveryId (nullable), status, address` — confirmar si Delivery necesita `code` (ver C-8) |
| Seguridad | Header `X-Internal-Api-Key` → filtro/validador en `SecurityConfig` (endpoints `/api/internal/**` requieren la clave interna y quedan fuera del JWT de cliente); rechazo 403 con clave inválida |
| Config | `INTERNAL_API_KEY` (default dev) |
| Mapeo IDs | Convertir `UUID` del dominio → `Long` del contrato interno (respetando el esquema bigint) |

---

## 10. Configuración

### Estado actual (CONFIRMADO)

| Archivo | Contenido relevante |
|---|---|
| `orders-service/.env.example` | `SPRING_PROFILES_ACTIVE=supabase`, `SUPABASE_URL` (host supabasekong real en sslip.io), `SUPABASE_SERVICE_ROLE_KEY` (placeholder), `DELIVERY_FEE`, `AUTH_SERVICE_URL=http://localhost:8081`, `RABBITMQ_*` |
| `application.properties` | `server.port=8083`, `supabase.url`, `supabase.service-role-key`, `spring.rabbitmq.*`, `orders.rabbitmq.*`, `orders.delivery-fee`, `orders.max-claim-per-route`, `orders.default-distance-km`, `orders.default-estimated-minutes` |
| `application-supabase.properties` | Excluye JDBC/JPA/Flyway; `auth.service.url=${AUTH_SERVICE_URL:http://localhost:8081}` |
| `Dockerfile` | JRE 21, expone 8083, `SERVER_PORT=8083` |
| `.env.bak` | Config antigua JDBC (`DATABASE_URL/USERNAME/PASSWORD`) — obsoleta |

### Configuración final requerida

```text
SUPABASE_URL                 ✓ existe          (conexión propia de Orders)
SUPABASE_SERVICE_ROLE_KEY    ✓ existe
AUTH_SERVICE_URL             ✓ existe          (hoy solo para validación JWT)
CATALOG_SERVICE_URL          ✗ NO EXISTE       → agregar  (http://localhost:8082)
DELIVERY_SERVICE_URL         ✗ NO EXISTE       → agregar  (http://localhost:8084)
INTERNAL_API_KEY             ✗ NO EXISTE       → agregar
ORDERS_SERVICE_URL           (no consumido internamente por Orders; usado por otros)
```

Concepto (NO implementado, siguiendo el plan):

```yaml
services:
  catalog:  { base-url: ${CATALOG_SERVICE_URL:http://localhost:8082} }
  auth:     { base-url: ${AUTH_SERVICE_URL:http://localhost:8081} }
  delivery: { base-url: ${DELIVERY_SERVICE_URL:http://localhost:8084} }
  internal-api-key: ${INTERNAL_API_KEY:dev-key}
```

Los endpoints internos deben consumirse entre servicios con `X-Internal-Api-Key`. `PENDIENTE DE CONFIRMACIÓN`: constante del header, valor distribuido por entorno y manejo de 403.

---

## 11. Tests actuales

| Test | Tipo | Qué cubre | Evidencia |
|---|---|---|---|
| `OrderDomainTest.java` (7 métodos) | Unitario puro | `validateSingleRestaurant`, subtotal, total, transición desde `ENTREGADO`, `assignDelivery`/status | `src/test/java/cl/flashdrop/orders/domain/model/OrderDomainTest.java` |
| `CreateOrderUseCaseTest.java` (2 métodos) | Unitario con Mockito (mocks de ports) | Crear pedido OK (calcula total, persiste, publica evento), producto inexistente → excepción | `src/test/java/cl/flashdrop/orders/application/usecase/CreateOrderUseCaseTest.java` |

**Brechas detectadas:**
- Sin tests de controllers (`OrderController`, `HealthController`); sin tests de `@WebMvcTest`/`MockMvc`.
- Sin tests de adapters (`SupabaseRestOrderRepositoryAdapter`, `SupabaseRestCatalogAdapter`, `SupabaseRestDeliveryAdapter`).
- Sin tests HTTP externos (MockRestServiceServer/WireMock) — necesario para los nuevos clients HTTP.
- Sin tests del endpoint interno.
- Testcontainers está en `pom.xml` pero **no se usa** en ningún test.
- Los tests existentes **no cubren** los flujos: `ListOrders`, `GetOrderDetail`, `UpdateOrderStatus`, `Claim`, `ListRoutes`.

**Pruebas necesarias después de la migración (PROMPT 4, alineadas con el plan §15):**
1. Unitarios: `CreateOrder` (productos válidos / no disponible / lista vacía), `UpdateOrderStatus` (transición válida/inválida), `Claim` (asigna delivery_id y status).
2. HTTP clients: `CatalogHttpClientAdapter` (ids válidos; catalog caído/timeout → excepción manejable), `AuthHttpClientAdapter` (usuario válido; inexistente → `Optional.empty`), `DeliveryHttpClientAdapter` (obtener repartidor; crear ruta 201).
3. Endpoint interno: `GET /api/internal/orders?ids=1,2,3` → órdenes existentes; `?ids=999` → array vacío.
4. Seguridad: `X-Internal-Api-Key` válida/ inválida (403).

---

## 12. Validación del análisis anterior

Contraste del documento `ORDERS_SERVICE_MIGRATION_CONTEXT.md` contra el código real.

| Hallazgo anterior | Estado actual | Evidencia | Acción requerida |
|---|---|---|---|
| "Orders está implementado en Spring Boot hexagonal" | **CONFIRMADO** | `orders-service/pom.xml`, estructura `application/domain/infrastructure`, ports definidos | Ninguna |
| "Orders accede a `products` y `restaurant` vía Supabase" | **CONFIRMADO** | `SupabaseRestCatalogAdapter.java:25-57` | Reemplazo por HTTP (PROMPT 2) |
| "Orders accede a `users`" | **CONFIRMADO** | `SupabaseRestDeliveryAdapter.java:42-65,79-102` (`/users`) | Reemplazo por Auth HTTP (PROMPT 2) |
| "Orders accede a `delivery` y `delivery_routes`" | **CONFIRMADO** | `SupabaseRestDeliveryAdapter.java:68-102`; `SupabaseRestOrderRepositoryAdapter.java:145-226` | Reemplazo por Delivery HTTP (PROMPT 2) |
| "`orders`, `order_items` y `client` son tablas propias de Orders" | **CONFIRMADO** (con matiz) | Schema `V1__init.sql`; `client` consultada por `SupabaseRestDeliveryAdapter` | En BD propia conservarlas (PROMPT 3) |
| "`restaurant` pertenece exclusivamente a Catalog" | **CONFIRMADO** | Schema + plan | Orders la consume solo por API |
| "Ports `CatalogPort` y `DeliveryPort` ya están definidos" | **CONFIRMADO** | `domain/port/CatalogPort.java`, `domain/port/DeliveryPort.java` | Mantener port; reemplazar adapters |
| "Reemplazo sugerido: `CatalogHttpClientAdapter`" | **PARCIAL** | No existe aún; port sí | Crear en PROMPT 2 |
| "Orders debe exponer `GET /api/internal/orders?ids=...`" | **INCORRECTO/INEXISTENTE hoy** | grep `internal` → 0 hits; sin controller | Crear en PROMPT 3 |
| "Endpoints internos de Auth/Catalog/Delivery existen (fase 1)" | **NO VERIFICABLE / NO EXISTEN** | grep `internal` en todo el repo → 0 hits; controllers reales revisados | Solicitar a los responsables que los expongan; validar contratos (pendientes C-1…C-7) |
| "IDs de contrato en formato Long" | **PARCIAL** | Delivery/Auth/Catalog usan `Long`; el dominio de Orders usa `UUID` (hack `toUuid`/`extractRawId`); `openapi.yaml` de Orders dice "DB UUID, servicio usa Long internamente" (contradictorio con el código) | Definir mapeo UUID↔Long en contratos internos |
| "Orden de migración: Fase 1 endpoints internos → Fase 2 adapters → Fase 3 BD → Fase 4 E2E" | **CONFIRMADO como plan; NO ejecutado** | No existe ningún endpoint interno | Bloqueo: los prompts 2 y 3 dependen de endpoints aún inexistentes (ver sección 14) |
| "Local ports 8081/8082/8083/8084" | **CONFIRMADO** | auth `application.yml` (8081), catalog `application.yaml` (8082), orders `application.properties` (8083), delivery `application.yml` (8084) | Ninguna |
| "ErrorResponse estándar `{status, error, message}`" | **INCORRECTO en Orders hoy** | Orders usa `{error, message, timestamp}` (`ErrorResponse.java`) y envuelve respuestas OK en `ApiResponse{success,message,data}`; Catalog/Delivery usan `ApiResponse` o DTO propio | Unificar formato de error en endpoints internos (C-10) |
| "`X-Internal-Api-Key` protege `/api/internal/**` (403)" | **NO VERIFICABLE** | Sin implementación en ningún servicio | Definir mecanismo (C-9) |
| "deivery_fee 2500, max-claim 3" | **CONFIRMADO** | `application.properties:42-45` | Mantener |

**Corrección importante al análisis previo:** el documento anterior trata los endpoints internos como si fueran parte del estado actual ("Al final deben existir..."). En el código real **no existe ninguno**. El documento describe el *target*, no el estado. Esto define el bloqueo central (sección 14).

---

## 13. Plan de implementación

Relación de hallazgos con los siguientes prompts.

### PROMPT 2 — Migración de adapters SQL → HTTP

- Crear `CatalogHttpClientAdapter` (implementa `CatalogPort`) usando `CATALOG_SERVICE_URL` → consumir C-1/C-2/C-3 (pendientes de confirmación). Campos requeridos por Orders: productos (id, restaurantId, name, description, price, image, available), restaurante (name, address), restaurant por userId.
- Crear cliente `AuthHttpClientAdapter` para datos de perfil (C-4) → cubrir las consultas a `users` hoy en `SupabaseRestDeliveryAdapter`.
- Crear `DeliveryHttpClientAdapter` para: repartidor por userId (C-5), crear ruta (C-6), actualizar estado de ruta (C-7). Hoy `OrderRepositoryPort` gestiona `delivery_routes`; mover esos 4 métodos fuera de `SupabaseRestOrderRepositoryAdapter` hacia el cliente HTTP.
- Mantener en persistencia propia: `orders`, `order_items` y `client` (la resolución de cliente se conserva en Orders).
- Manejo de errores: traducción de outage (503/`SERVICE_UNAVAILABLE`) y 404 → `Optional.empty` donde corresponda (no 500 genérico).
- Config: agregar `CATALOG_SERVICE_URL`, `DELIVERY_SERVICE_URL`, `INTERNAL_API_KEY` (values pendientes).
- **Depende de:** confirmación de contratos C-1…C-7 (bloqueo).

### PROMPT 3 — BD propia + endpoint interno

- Diseñar migración solo-Orders (`orders`, `order_items`, `client`) **sin** FKs a `restaurant`, `delivery`, `products`, `users` (columnas planas bigint).
- Seed propio de Orders.
- `.env`/conexión Supabase propia (`SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` apuntando a la nueva BD).
- Crear `GET /api/internal/orders?ids=...` (C-8): controller, método en port/repositorio (`findByIds` ligero o `findByIdsForClaim`), DTO array-stable, IDs inexistentes ignorados, ignorados deliveryId nullable, `status`/`address` no null.
- Seguridad interna: `X-Internal-Api-Key` en `/api/internal/**`, rechazo 403 (C-9), dejar `/api/internal/**` fuera del JWT de cliente.
- Unificar formato de error (C-10) para el endpoint interno.

### PROMPT 4 — Testing + validación completa

- Unitarios de dominio/usecases (agregar los que faltan: UpdateStatus, Claim, GetDetail, List).
- Tests de los 3 HTTP clients con MockRestServiceServer/WireMock (éxito, 404, timeout/caída).
- Tests del endpoint interno (ids existentes / inexistentes / clave API).
- Validación E2E con los 4 servicios y BDs propias; verificar ausencia de consultas a tablas ajenas.
- Verificación del criterio de término del plan (§16): sin SQL directo a tablas externas, endpoint interno operativo, protegido, seed propio, tests mínimos (14).

---

## 14. Riesgos, bloqueos e información faltante

### Bloqueos que impiden implementar hoy

1. **No existen los endpoints internos** de Catalog (`C-1`, `C-2`, `C-3`), Auth (`C-4`) ni Delivery (`C-5`, `C-6`, `C-7`). Sin ellos, PROMPT 2 no puede producir un cliente HTTP verificable. **Grep `internal` en todo el repo → 0 hits.** (Acción: coordinación con Nicolás/Javier/Sebastián.)
2. **`X-Internal-Api-Key` no está implementado en ningún servicio** (C-9): no hay base real para exigirlo ni para probarlo. Definir distribución del secreto y formato de rechazo (403).
3. **Contrato `GET /api/internal/orders` no definido operativamente** con Delivery: Delivery espera un campo `code` en las órdenes (evidencia: `delivery-service/.../OrderRow.java` y `OrderServiceClientAdapter`), que el plan (C-8) no incluye. Decidir si el endpoint interno lo devuelve.

### Información que debe confirmarse (no inventar)

- Forma JSON exacta y nullabilidad de C-1…C-7 (método HTTP, path, query, body, códigos de respuesta, mensajes de 404/409).
- Si Auth permite un endpoint de perfil interno por `userId` sin JWT de cliente (hoy solo `/auth/profile`, basado en token).
- Semántica de `client` cuando `userId` es null (modo demo en `findClientIdByUserId`) y si se conserva en la BD de Orders.
- Unificación del `ErrorResponse` estándar `{status, error, message}` (hoy Orders usa `{error, message, timestamp}`).
- Formato de IDs en los contratos internos: el plan usa `Long`; el dominio de Orders usa `UUID` (`toUuid`/`extractRawId`). Confirmar el mapeo definitivo (p.ej. si Catalog/Delivery entregan `Long` sencillos — sí, sus DTOs usan `Long`).
- Valores de `CATALOG_SERVICE_URL`, `DELIVERY_SERVICE_URL`, `INTERNAL_API_KEY` por entorno (no definidos en ninguno de los `.env.example`).

### Problemas que pueden resolverse durante la implementación

- Migración de `delivery_routes` fuera de `SupabaseRestOrderRepositoryAdapter` (refactor puramente interno + contrato C-6/C-7 confirmado).
- Dormir/eliminar `V1__init.sql` y `V1_1__seed_local_data.sql` (reinventar schema + seed solo-Orders).
- FKs externas en el schema (eliminarlas en la BD nueva).
- `ClaimDeliveryOrdersUseCase` y `ListDeliveryRoutesUseCase` huérfanos (sin controller): decidir si se exponen en `DeliveryController` de Orders o si el act flip declaim/route pasa íntegro a Delivery Service.
- Archivo basura `infrastructure/persistence/adapter/test.txt` (`hello`).
- `.env.bak` obsoleto (credenciales JDBC antiguas) — no debe usarse.
- Contradicción en `openapi.yaml` y README de Orders (endpoints `/api/delivery/*` sin controller) vs `openapi.yaml` nota "DB UUID, servicio usa Long" — documentar antes de implementar.
- Testcontainers declarado pero sin uso — decidir si se usa en PROMPT 4 (recomendado: MockRestServiceServer para clients HTTP; no requiere contenedores).

---

*Fin del informe. Toda afirmación técnica marcada como `CONFIRMADO` tiene cita a archivo y ubicación dentro del repositorio actual.*