# Tasks: `store-flow`

> **Revisión aplicada** (Javier, 2026-09-24): se eliminaron T-10/T-11/T-12 (HTTP self-call con cache Caffeine) — catalog resuelve ownership localmente con `GetRestaurantByUserIdUseCase` existente. Se agregó `SecurityConfig` con Spring Security + JWT RS256. Se cambió `store_owner` → `Restaurante`. Se agregó necesidad de código (no solo YAML) en PR-gateway-2 para multipart. Se cambió `image` para aceptar object key, no URL.

## Pre-work (antes de los PRs)

### P-0 — Alinear Spring Boot del monorepo
- **Files**: `services/build.gradle.kts`
- **Acción**: decidir entre alinear versión raíz a 3.5.16 (consistente con catalog) o retirar catalog del build raíz (proyecto Gradle autónomo). Recomendación: alinear raíz a 3.5.16
- **Commit**: `build(monorepo): align Spring Boot version to 3.5.16 across root build`

### P-1 — Agregar `catalog-service-ci.yml`
- **Files**: `.github/workflows/catalog-service-ci.yml`
- **Acceptance**: ejecuta `./gradlew test` desde `services/catalog-service/`. Corre en push a `main` y en PRs que toquen `services/catalog-service/**`
- **Commit**: `ci(catalog): add catalog-service-ci workflow`

### P-2 — Aprovisionar S3 en Floci
- **Files**: `infra/coolify/env.shared.template`, task definition ECS, política de bucket, secrets
- **Acceptance**: bucket `flashdrop-products` creado en Floci; endpoint accesible desde el contenedor catalog; vars `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION`, `S3_PUBLIC_URL_BASE` configuradas; CORS habilitado para `https://app.flashdrop.cl`; test de upload devuelve 200
- **Bloquea**: PR-catalog-image

## PR Chain

**Strategy**: parallel-by-owner (cada dev dueño de su servicio mergea su PR en cualquier orden; `PR-gateway-2` espera al final).

```
main
  ├── feat/catalog-s3-image-upload                   (PR-catalog-image, bloqueado por P-2)
  │     └── feat/catalog-my-products-crud              (PR-catalog-products, depende del anterior)
  ├── feat/orders-restaurant-metrics                   (PR-orders-metrics, independiente)
  └── feat/gateway-add-store-routes                    (PR-gateway-2, último, incluye código multipart)
```

---

## PR-catalog-image — `feat/catalog-s3-image-upload`

**Branch**: `feat/catalog-s3-image-upload` (base: `main` después de P-0, P-1, P-2)
**Dev**: catalog
**Scope**: Spring Security base + AWS SDK + endpoint image + multipart
**Est. LOC**: ~400 | **Files**: ~12

---

### T-1 — Dependencias en `build.gradle.kts`
- **Files**: `services/catalog-service/build.gradle.kts`
- **TDD RED first**: N/A (build file)
- **Acceptance**: agregar `org.springframework.boot:spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `software.amazon.awssdk:s3`, `software.amazon.awssdk:netty-nio-client`, `software.amazon.awssdk:auth`. Compila
- **Commit**: `build(catalog): add spring-security oauth2-resource-server and aws-sdk-java-v2`

### T-2 — `SecurityConfig` con JWT RS256
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/config/SecurityConfig.java` (NUEVO)
- **TDD RED first**: sí — `SecurityConfigTest` con `@WebMvcTest` verifica 401 sin JWT, 403 con JWT sin `Restaurante`, 200 con JWT válido y rol `Restaurante`
- **Acceptance**: configura `SecurityFilterChain` con `oauth2ResourceServer().jwt()` apuntando a `AUTH_JWKS_URI`. `requestMatchers("/api/catalog/my/**")` requiere rol `Restaurante`. `requestMatchers("/catalog/**", "/api/internal/**", "/actuator/health/**", "/actuator/info", "/actuator/prometheus")` permitAll. `anyRequest()` authenticated (o denyAll). Filtro después de `CorrelationIdFilter` y antes de `InternalApiKeyFilter`
- **Commit**: `feat(catalog): add SecurityConfig with JWT RS256 and Restaurante authorization`

### T-3 — `JwtAuthoritiesMapper` (rol → authority)
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/config/JwtAuthoritiesMapper.java` (NUEVO)
- **TDD RED first**: sí — test unitario del mapper
- **Acceptance**: convierte `roles[]` claim a authorities `ROLE_Restaurante`, `ROLE_Cliente`, etc. Si el claim no existe o está vacío, devuelve lista vacía (el `requestMatchers` rechaza con 403)
- **Commit**: `feat(catalog): map JWT roles to Spring Security authorities`

### T-4 — Manejo de 401/403 en `RestExceptionHandler`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/RestExceptionHandler.java`
- **TDD RED first**: cubierto indirectamente por `SecurityConfigTest`
- **Acceptance**: agregar handlers para `AuthenticationException` → 401, `AccessDeniedException` → 403. Si se adopta `ApiError` de shared-observability, agregar la dependencia y migrar `ErrorResponse`. Si no, mantener `ErrorResponse` y documentar
- **Commit**: `fix(catalog): add 401 and 403 handlers in RestExceptionHandler`

### T-5 — Configuración S3 en `application.yml`
- **Files**: `services/catalog-service/src/main/resources/application.yml` + `application-local.yml`
- **TDD RED first**: N/A (config)
- **Acceptance**: variables `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION`, `S3_PUBLIC_URL_BASE` leídas desde env. Defaults sensatos para dev local (Floci)
- **Commit**: `feat(catalog): add S3 client config with environment variables`

### T-6 — Puerto outbound `ProductImageStorage`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/application/port/outbound/ProductImageStorage.java`
- **TDD RED first**: sí — test de la interfaz con mock
- **Acceptance**: interfaz con `StoredImage upload(byte[] content, String contentType, String keyPrefix)` donde `StoredImage` es un record con `objectKey` y `url`. También `void delete(String key)` para futuro. Lanza `ImageStorageException` ante fallo
- **Commit**: `feat(catalog): add ProductImageStorage outbound port returning objectKey and url`

### T-7 — Adapter `S3ProductImageStorage`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/outbound/storage/S3ProductImageStorage.java`
- **TDD RED first**: sí — `S3ProductImageStorageTest` con LocalStack o mock client
- **Acceptance**: implementa el puerto. Usa `software.amazon.awssdk.services.s3.S3Client`. Genera key `products/{yyyy}/{mm}/{uuid}.{ext}`. Sube bytes. Devuelve `StoredImage(objectKey, url)` donde `url = S3_PUBLIC_URL_BASE + "/" + objectKey`
- **Commit**: `feat(catalog): implement S3ProductImageStorage returning objectKey and url`

### T-8 — Config bean `StorageConfig`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/config/StorageConfig.java`
- **TDD RED first**: N/A (wiring)
- **Acceptance**: `@Bean` para `S3Client` (configurado con credenciales y endpoint de env) y `ProductImageStorage`. Lee las env vars
- **Commit**: `chore(catalog): register S3Client and ProductImageStorage beans`

### T-9 — Use case `UploadProductImageUseCase`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/UploadProductImageUseCase.java`
- **TDD RED first**: sí — `UploadProductImageUseCaseTest` con `ProductImageStorage` mockeado
- **Acceptance**: valida MIME ∈ {jpeg, png, webp} (400 `BAD_REQUEST` si otro), tamaño ≤ 5MB (413 `PAYLOAD_TOO_LARGE` si excede), genera key, llama al storage, devuelve `StoredImage`
- **Commit**: `feat(catalog): add UploadProductImageUseCase with mime and size validation`

### T-10 — Endpoint `POST /api/catalog/my/products/image`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/MyStoreImageController.java`
- **TDD RED first**: sí — `MyStoreImageControllerTest` + `MyStoreImageControllerIT`
- **Acceptance**: handler con `@RequestParam("file") MultipartFile file`. JWT `Restaurante`. Llama al use case. Devuelve 201 con `{objectKey, url}`. Maneja errores correctamente (400, 413, 502)
- **Commit**: `feat(catalog): add POST /api/catalog/my/products/image endpoint`

### T-11 — Validación multipart global (límite de tamaño)
- **Files**: `services/catalog-service/src/main/resources/application.yml`
- **TDD RED first**: N/A (config)
- **Acceptance**: `spring.servlet.multipart.max-file-size: 6MB`, `max-request-size: 6MB`. Spring rechaza con 413 antes del controller si excede
- **Commit**: `chore(catalog): configure multipart max-file-size to 6MB`

### T-12 — Variables S3 en `env.shared.template`
- **Files**: `infra/coolify/env.shared.template`
- **Acceptance**: vars S3 documentadas con defaults y notas sobre Floci
- **Commit**: `docs(infra): document S3 environment variables in env.shared.template`

### T-13 — `RestExceptionHandler`: 413 y 502
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/RestExceptionHandler.java`
- **Acceptance**: agregar handler para `MaxUploadSizeExceededException` → 413 `PAYLOAD_TOO_LARGE`, `ImageStorageException` → 502 `BAD_GATEWAY`
- **Commit**: `fix(catalog): add 413 and 502 handlers in RestExceptionHandler`

---

## PR-catalog-products — `feat/catalog-my-products-crud`

**Branch**: `feat/catalog-my-products-crud` (base: `main` después de `PR-catalog-image`)
**Dev**: catalog
**Scope**: namespace `/api/catalog/my/products` CRUD con ownership **local** + filtrar `is_available` en queries públicas
**Est. LOC**: ~280 | **Files**: ~9

---

### T-14 — Queries con filtro `is_available`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/application/port/outbound/ProductRepositoryPort.java` + `infrastructure/adapter/outbound/persistence/jpa/repository/SpringDataProductRepository.java` + adaptadores
- **TDD RED first**: sí — `JpaProductRepositoryAdapterTest` con H2
- **Acceptance**: agregar `List<Product> findAllAvailable()`, `List<Product> findByCategoryIdAndIsAvailableTrue(Long categoryId)`, `List<Product> findByRestaurantIdAndIsAvailableTrue(Long restaurantId)`. Mantener los métodos existentes (`findAll`, `findByCategoryId`, `findByRestaurantId`) para uso owner sin filtro
- **Commit**: `feat(catalog): add isAvailable=true queries for public catalog`

### T-15 — `ListProductsUseCase` con métodos disponibles
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/ListProductsUseCase.java`
- **TDD RED first**: sí — actualizar test existente
- **Acceptance**: agregar `executeAvailable()` y `executeAvailable(categoryId, restaurantId)` que llaman a los métodos con filtro. Mantener los originales `execute()` y `execute(categoryId, restaurantId)` sin filtro (para owner)
- **Commit**: `feat(catalog): filter ListProductsUseCase by is_available for public catalog`

### T-16 — `ProductController` usa métodos disponibles
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/ProductController.java`
- **TDD RED first**: cubierto por test existente de `ProductController`
- **Acceptance**: cambiar `listProducts()` para llamar a `executeAvailable()`. Test verifica que productos con `available=false` no aparecen en `/catalog/products`
- **Commit**: `fix(catalog): public catalog endpoints now filter is_available=true`

### T-17 — Use cases owner (4) — **sin HTTP self-call**
- **Files**:
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/OwnerCreateProductUseCase.java`
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/OwnerListProductsUseCase.java`
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/OwnerUpdateProductUseCase.java`
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/OwnerDeleteProductUseCase.java`
- **TDD RED first**: sí — un test por use case
- **Acceptance**:
  - `OwnerCreateProductUseCase`: recibe `(userId, command)` → inyecta `GetRestaurantByUserIdUseCase`, llama `getRestaurantByUserId.execute(userId)`, si `Optional.empty()` → 403, sino crea con `restaurantId` derivado.
  - `OwnerListProductsUseCase`: recibe `userId` → resuelve `restaurantId` local → lista productos por restaurante (sin filtro `available`, incluye inactivos).
  - `OwnerUpdateProductUseCase`: recibe `(userId, productId, command)` → resuelve `restaurantId` local → verifica que `product.getRestaurantId() == resolved` (403 si no) → edita usando `UpdateProductUseCase` existente.
  - `OwnerDeleteProductUseCase`: recibe `(userId, productId)` → verifica ownership → marca `available=false`.
- **Commit**: `feat(catalog): add Owner* use cases using local GetRestaurantByUserIdUseCase`

### T-18 — DTOs owner
- **Files**:
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/dto/OwnerCreateProductRequest.java`
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/dto/OwnerUpdateProductRequest.java`
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/dto/OwnerProductCommand.java`
- **Acceptance**: records con validaciones Jakarta Validation. `image` es `String` (object key, formato `products/{yyyy}/{mm}/{uuid}.{ext}`). Sin `restaurantId` en ningún DTO
- **Commit**: `feat(catalog): add OwnerCreate and OwnerUpdate request DTOs without restaurantId`

### T-19 — `MyStoreProductController` con los 4 endpoints
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/MyStoreProductController.java`
- **TDD RED first**: sí — `MyStoreProductControllerIT` con todos los casos antes
- **Acceptance**: `POST/GET /api/catalog/my/products`, `PUT/DELETE /api/catalog/my/products/{id}`. JWT `Restaurante` (validado por `SecurityConfig`). Extrae `userId` del JWT. Delega a use cases owner. Maneja 401, 403, 404 correctamente
- **Commit**: `feat(catalog): add MyStoreProductController with full CRUD and Restaurante role`

### T-20 — `MyStoreProductControllerIT`: tests IDOR + smoke
- **Files**: `services/catalog-service/src/test/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/MyStoreProductControllerIT.java`
- **Acceptance**: test donde un `Restaurante` intenta `PUT /api/catalog/my/products/{productId}` de otro restaurante → 403. Idem para DELETE. Test con JWT sin `Restaurante` → 403. Test sin JWT → 401
- **Commit**: `test(catalog): add IDOR and auth integration tests for owner product endpoints`

### T-21 — Test filtrado catálogo público
- **Files**: `services/catalog-service/src/test/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/ProductControllerIT.java` (actualizar existente)
- **Acceptance**: tests que verifican que `GET /catalog/products` con un producto `available=false` no lo devuelve. Test mix: 3 productos con `available=true` y 2 con `available=false`, verifica que el endpoint devuelve los 3
- **Commit**: `test(catalog): add isAvailable filtering tests for public catalog`

---

## PR-orders-metrics — `feat/orders-restaurant-metrics`

**Branch**: `feat/orders-restaurant-metrics` (base: `main`)
**Dev**: orders
**Scope**: `GET /api/orders/restaurants/{id}/sales-summary`
**Est. LOC**: ~250 | **Files**: ~7

> **Sin cache Caffeine**. Orders llama a catalog por HTTP en cada request. La DB está indexada, no necesita cache.

---

### T-22 — DTO `SalesSummaryResponse`
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/application/dto/SalesSummaryResponse.java`
- **TDD RED first**: N/A (record simple)
- **Acceptance**: record con `restaurantId`, `range`, `from`, `to`, `totalOrders`, `totalRevenue`, `averageTicket`, `topProducts` (lista de records anidados)
- **Commit**: `feat(orders): add SalesSummaryResponse DTO`

### T-23 — Query method en repositorio
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/domain/port/OrderRepositoryPort.java` + JPA impl
- **TDD RED first**: sí — test del repo
- **Acceptance**: `List<Order> findByRestaurantAndStatusAndCreatedAtBetween(UUID restaurantId, Collection<OrderStatus> statuses, OffsetDateTime from, OffsetDateTime to)` o múltiples métodos si se prefiere segregar
- **Commit**: `feat(orders): add findByRestaurantAndStatusAndCreatedAtBetween query`

### T-24 — Puerto `RestaurantOwnershipPort` en orders-service
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/domain/port/RestaurantOwnershipPort.java`
- **TDD RED first**: sí — test del puerto
- **Acceptance**: interfaz `Optional<UUID> resolveRestaurantId(Long userId)`. **Sin cache** — cada llamada hace un HTTP GET. Si falla (timeout, 5xx), devuelve `Optional.empty()` y el use case retorna 503
- **Commit**: `feat(orders): add RestaurantOwnershipPort outbound port (no cache)`

### T-25 — Adapter HTTP del ownership port en orders-service
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/infrastructure/adapter/outbound/http/HttpRestaurantOwnershipAdapter.java`
- **Acceptance**: llama a `GET http://catalog-service:8082/api/internal/restaurants?userId={userId}` con header `X-Internal-Api-Key`. Parsea respuesta. Si 404 o vacío → `Optional.empty()`. Si 5xx → log + propagar para que el use case retorne 503
- **Commit**: `feat(orders): implement HttpRestaurantOwnershipAdapter (no cache)`

### T-26 — Use case `GetRestaurantSalesSummaryUseCase`
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/application/usecase/GetRestaurantSalesSummaryUseCase.java`
- **TDD RED first**: sí — `GetRestaurantSalesSummaryUseCaseTest` con dataset controlado
- **Acceptance**: recibe `(userId, restaurantId, range)`. Llama `RestaurantOwnershipPort.resolveRestaurantId(userId)`. Si no coincide con `restaurantId` del path → 403. Calcula from/to según range. Query órdenes ENTREGADO en ese rango. Calcula totalOrders, totalRevenue, averageTicket, topProducts (5 más vendidos). Devuelve `SalesSummaryResponse`
- **Commit**: `feat(orders): add GetRestaurantSalesSummaryUseCase with aggregations`

### T-27 — `SalesSummaryController` (o método en OrderController)
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/infrastructure/api/SalesSummaryController.java` (nuevo) o método en `OrderController`
- **TDD RED first**: sí — `SalesSummaryControllerIT`
- **Acceptance**: `GET /api/orders/restaurants/{id}/sales-summary?range=day|week|month`. JWT `Restaurante` (validado por el `SecurityConfig` de orders). Delega al use case. Devuelve `SalesSummaryResponse`
- **Commit**: `feat(orders): add GET /api/orders/restaurants/{id}/sales-summary endpoint`

### T-28 — Tests de agregaciones con dataset controlado
- **Files**: `services/orders-service/src/test/java/cl/flashdrop/orders/application/usecase/GetRestaurantSalesSummaryUseCaseIT.java`
- **Acceptance**: tests con 10 órdenes de prueba (varios productos, varios estados, varios días). Verifica counts, sums, averages, topProducts. Incluye caso "ninguna orden" → totales en cero
- **Commit**: `test(orders): add sales summary aggregation integration tests`

### T-29 — Evaluar índice (opcional)
- **Files**: `services/orders-service/src/main/resources/db/migration/V{n}__idx_orders_restaurant_status_created.sql` (solo si el EXPLAIN lo justifica)
- **Acceptance**: índice `CREATE INDEX IF NOT EXISTS idx_orders_restaurant_status_created ON orders(restaurant_id, status, created_at DESC)`. Solo si PR-orders-metrics lo requiere
- **Commit**: `perf(orders): add composite index for sales summary aggregation` (si aplica)

---

## PR-gateway-2 — `feat/gateway-add-store-routes`

**Branch**: `feat/gateway-add-store-routes` (base: `main` después de los 3 PRs anteriores)
**Dev**: gateway
**Scope**: **código nuevo** para multipart + 2 rutas nuevas
**Est. LOC**: ~150 | **Files**: ~4

---

### T-30 — Parser multipart en gateway
- **Files**: `gateway/src/index.ts` o nuevo `gateway/src/multipart.ts`
- **TDD RED first**: sí — test unitario del plugin
- **Acceptance**: registrar `@fastify/multipart` (o equivalente). Configurar `bodyLimit: 6291456` (6MB). Si body excede, devolver 413 sin pasar al backend
- **Commit**: `feat(gateway): add multipart parser with 6MB bodyLimit`

### T-31 — Passthrough raw stream en `engine.ts`
- **Files**: `gateway/src/proxy/engine.ts`
- **TDD RED first**: sí — test del proxy con multipart
- **Acceptance**: cuando el `Content-Type` empieza con `multipart/form-data`, **NO** serializar el body con `JSON.stringify`. Reenviar el raw stream al backend preservando `Content-Type` (con boundary) y `Content-Length`/`Transfer-Encoding`. Para todos los demás content-types, mantener el comportamiento actual
- **Commit**: `feat(gateway): passthrough raw stream for multipart bodies in proxy`

### T-32 — Rutas nuevas en `gateway.yaml`
- **Files**: `gateway/config/*.yaml`
- **TDD RED first**: N/A (config)
- **Acceptance**:
  - `GET /api/orders/restaurants/{id}/sales-summary` → `orders-service:8083`
  - `POST/GET/PUT/DELETE /api/catalog/my/products[/{id}]` → `catalog-service:8082`
  - `POST /api/catalog/my/products/image` → `catalog-service:8082`
- **Commit**: `chore(gateway): add store-flow routes`

### T-33 — Test de integración: imagen real a través del gateway
- **Files**: `gateway/tests/integration/multipart-upload.test.ts` (NUEVO)
- **Acceptance**: test que envía una imagen real (~4MB jpeg) a través del gateway al endpoint `/api/catalog/my/products/image` y verifica que (a) llega al backend con los bytes correctos, (b) el `Content-Type` con boundary se preserva, (c) el backend responde 201 con `{objectKey, url}`
- **Commit**: `test(gateway): add integration test for multipart upload end-to-end`

---

## Orden de merge recomendado

1. **Pre-work** P-0 (Spring Boot), P-1 (CI workflow), P-2 (S3 en Floci)
2. `PR-catalog-image` primero (incluye Spring Security base; es prerrequisito de `PR-catalog-products`)
3. `PR-catalog-products` después (depende del primero)
4. `PR-orders-metrics` en paralelo desde el inicio (es independiente; no comparte archivos con catalog)
5. `PR-gateway-2` último (incluye multipart + rutas; depende de que los otros estén mergeados a `main`)

Los archivos tocados por cada PR son disjuntos entre servicios. Solo dentro de catalog hay dependencia secuencial (image → products).