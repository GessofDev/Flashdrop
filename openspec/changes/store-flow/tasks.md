# Tasks: `store-flow`

## PR Chain

**Strategy**: parallel-by-owner (cada dev dueño de su servicio mergea su PR en cualquier orden; `PR-gateway-2` espera al final).

```
main
  ├── feat/catalog-s3-image-upload                   (PR-catalog-image)
  │     └── feat/catalog-my-products-crud              (PR-catalog-products)
  ├── feat/orders-restaurant-metrics                   (PR-orders-metrics)
  └── feat/gateway-add-store-routes                    (PR-gateway-2, último)
```

`PR-catalog-products` depende de `PR-catalog-image` (comparten SecurityConfig + application.yml). El resto son paralelos.

---

## PR-catalog-image — `feat/catalog-s3-image-upload`

**Branch**: `feat/catalog-s3-image-upload` (base: `main`)
**Dev**: catalog
**Scope**: S3/MinIO client config + endpoint `POST /api/catalog/my/products/image`
**Est. LOC**: ~250 | **Files**: ~7

---

### T-1 — Dependencias AWS SDK en `build.gradle.kts`
- **Files**: `services/catalog-service/build.gradle.kts`
- **TDD RED first**: N/A (build file)
- **Acceptance**: `software.amazon.awssdk:s3`, `software.amazon.awssdk:netty-nio-client`, `software.amazon.awssdk:auth` agregados. Compila
- **Commit**: `build(catalog): add aws-sdk-java-v2 s3 netty auth`

### T-2 — Configuración S3 en `application.yml` y profile
- **Files**:
  - `services/catalog-service/src/main/resources/application.yml`
  - `services/catalog-service/src/main/resources/application-local.yml` (override dev)
- **TDD RED first**: N/A (config)
- **Acceptance**: variables `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION`, `S3_PUBLIC_URL_BASE` leídas desde env. Defaults sensatos para dev
- **Commit**: `feat(catalog): add S3 client config with environment variables`

### T-3 — Puerto outbound `ProductImageStorage`
- **Files**:
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/port/outbound/ProductImageStorage.java`
- **TDD RED first**: sí — test de la interfaz con mock
- **Acceptance**: interfaz con `String upload(byte[] content, String contentType, String keyPrefix)` y `void delete(String key)` para futuro. Lanza `ImageStorageException` ante fallo
- **Commit**: `feat(catalog): add ProductImageStorage outbound port`

### T-4 — Adapter `S3ProductImageStorage`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/outbound/storage/S3ProductImageStorage.java`
- **TDD RED first**: sí — `S3ProductImageStorageTest` con LocalStack o mock client
- **Acceptance**: implementa el puerto. Usa `software.amazon.awssdk.services.s3.S3Client`. Genera key única (`UUID + ext`). Sube bytes. Devuelve URL pública o firmada según config
- **Commit**: `feat(catalog): implement S3ProductImageStorage adapter`

### T-5 — Config bean `StorageConfig`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/config/StorageConfig.java`
- **TDD RED first**: N/A (wiring)
- **Acceptance**: `@Bean` para `S3Client` y `ProductImageStorage`. Lee las env vars y configura el cliente
- **Commit**: `chore(catalog): register S3Client and ProductImageStorage beans`

### T-6 — Use case `UploadProductImageUseCase`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/UploadProductImageUseCase.java`
- **TDD RED first**: sí — `UploadProductImageUseCaseTest` con `ProductImageStorage` mockeado
- **Acceptance**: valida MIME ∈ {jpeg, png, webp} (400 si otro), tamaño ≤ 5MB (413 si excede), genera key, llama al storage, devuelve URL
- **Commit**: `feat(catalog): add UploadProductImageUseCase with mime and size validation`

### T-7 — Endpoint `POST /api/catalog/my/products/image`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/MyStoreImageController.java`
- **TDD RED first**: sí — `MyStoreImageControllerTest` + `MyStoreImageControllerIT`
- **Acceptance**: handler con `@RequestParam("file") MultipartFile file`. JWT store_owner. Llama al use case. Devuelve `{url}` en 201. Maneja errores correctamente
- **Commit**: `feat(catalog): add POST /api/catalog/my/products/image endpoint`

### T-8 — Validación multipart global (límite de tamaño)
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/config/MultipartConfig.java` o properties en `application.yml`
- **TDD RED first**: N/A (config)
- **Acceptance**: `spring.servlet.multipart.max-file-size: 5MB`. Spring rechaza con 413 antes de llegar al controller si excede
- **Commit**: `chore(catalog): configure multipart max-file-size to 5MB`

### T-9 — Variables de entorno en `infra/coolify/env.shared.template`
- **Files**: `infra/coolify/env.shared.template`
- **TDD RED first**: N/A (config docs)
- **Acceptance**: variables S3 documentadas con defaults y notas sobre Floci
- **Commit**: `docs(infra): document S3 environment variables in env.shared.template`

---

## PR-catalog-products — `feat/catalog-my-products-crud`

**Branch**: `feat/catalog-my-products-crud` (base: `main` después de `PR-catalog-image`)
**Dev**: catalog
**Scope**: namespace `/api/catalog/my/products` CRUD con ownership derivada del JWT
**Est. LOC**: ~350 | **Files**: ~10

---

### T-10 — Puerto `RestaurantOwnershipResolver`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/application/port/outbound/RestaurantOwnershipResolver.java`
- **TDD RED first**: sí — `RestaurantOwnershipResolverTest` con HTTP mock
- **Acceptance**: interfaz con `Optional<Long> resolveRestaurantId(Long userId)`. Implementación HTTP en infrastructure con cache Caffeine TTL 60s. Lanza excepción si la llamada interna falla (fail-closed)
- **Commit**: `feat(catalog): add RestaurantOwnershipResolver outbound port`

### T-11 — Adapter HTTP `HttpRestaurantOwnershipResolver`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/outbound/http/HttpRestaurantOwnershipResolver.java`
- **TDD RED first**: cubierto por T-10
- **Acceptance**: llama a `GET http://catalog-service:8082/api/internal/restaurants?userId={userId}` con header `X-Internal-Api-Key`. Parsea respuesta. Cache TTL 60s. Si 404 o vacío → `Optional.empty()`
- **Commit**: `feat(catalog): implement HttpRestaurantOwnershipResolver with caffeine cache`

### T-12 — Config bean del resolver
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/config/StorageConfig.java` (mismo archivo de T-5)
- **Acceptance**: `@Bean` para `RestaurantOwnershipResolver`. Configurar Caffeine cache
- **Commit**: `chore(catalog): register RestaurantOwnershipResolver bean`

### T-13 — Use cases owner (4)
- **Files**:
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/OwnerCreateProductUseCase.java`
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/OwnerListProductsUseCase.java`
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/OwnerUpdateProductUseCase.java`
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/usecase/OwnerDeleteProductUseCase.java`
- **TDD RED first**: sí — un test por use case
- **Acceptance**:
  - `OwnerCreateProductUseCase`: recibe `(userId, command)` → resuelve restaurantId, valida, crea con `restaurantId` derivado.
  - `OwnerListProductsUseCase`: recibe `userId` → resuelve restaurantId → lista productos por restaurante.
  - `OwnerUpdateProductUseCase`: recibe `(userId, productId, command)` → resuelve restaurantId → valida ownership (403 si no coincide) → edita.
  - `OwnerDeleteProductUseCase`: recibe `(userId, productId)` → valida ownership → marca `available=false`.
- **Commit**: `feat(catalog): add OwnerCreateProductUseCase with ownership derivation`

### T-14 — DTOs owner
- **Files**:
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/dto/OwnerCreateProductRequest.java`
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/dto/OwnerUpdateProductRequest.java`
  - `services/catalog-service/src/main/java/com/flashdrop/catalog/application/dto/OwnerProductCommand.java`
- **Acceptance**: records con validaciones Jakarta Validation. Sin `restaurantId` en ningún DTO
- **Commit**: `feat(catalog): add OwnerCreate and OwnerUpdate request DTOs without restaurantId`

### T-15 — `MyStoreProductController` con los 4 endpoints
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/MyStoreProductController.java`
- **TDD RED first**: sí — `MyStoreProductControllerIT` con todos los casos antes
- **Acceptance**: `POST/GET /api/catalog/my/products`, `PUT/DELETE /api/catalog/my/products/{id}`. JWT store_owner. Extrae `userId` del JWT. Delega a use cases. Maneja 403 correctamente
- **Commit**: `feat(catalog): add MyStoreProductController with full CRUD`

### T-16 — SecurityConfig para `/api/catalog/my/**`
- **Files**: `services/catalog-service/src/main/java/com/flashdrop/catalog/infrastructure/config/SecurityConfig.java` (o equivalente)
- **Acceptance**: las rutas `/api/catalog/my/**` requieren JWT con rol `store_owner`. Las rutas públicas existentes (`/catalog/*`) siguen iguales
- **Commit**: `chore(catalog): restrict /api/catalog/my/** to store_owner role`

### T-17 — Tests IDOR
- **Files**: `services/catalog-service/src/test/java/com/flashdrop/catalog/infrastructure/adapter/inbound/rest/MyStoreProductControllerIT.java`
- **Acceptance**: test donde un store_owner intenta `PUT /api/catalog/my/products/{productId}` de otro restaurante → 403. Idem para DELETE
- **Commit**: `test(catalog): add IDOR integration tests for owner product endpoints`

---

## PR-orders-metrics — `feat/orders-restaurant-metrics`

**Branch**: `feat/orders-restaurant-metrics` (base: `main`)
**Dev**: orders
**Scope**: `GET /api/orders/restaurants/{id}/sales-summary`
**Est. LOC**: ~250 | **Files**: ~7

---

### T-18 — DTO `SalesSummaryResponse`
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/application/dto/SalesSummaryResponse.java`
- **TDD RED first**: N/A (record simple)
- **Acceptance**: record con `restaurantId`, `range`, `from`, `to`, `totalOrders`, `totalRevenue`, `averageTicket`, `topProducts` (lista de records anidados)
- **Commit**: `feat(orders): add SalesSummaryResponse DTO`

### T-19 — Query method en repositorio
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/domain/port/OrderRepositoryPort.java` + JPA impl
- **TDD RED first**: sí — test del repo
- **Acceptance**: `List<Order> findByRestaurantAndStatusAndCreatedAtBetween(UUID restaurantId, Collection<OrderStatus> statuses, OffsetDateTime from, OffsetDateTime to)` o múltiples métodos si se prefiere segregar
- **Commit**: `feat(orders): add findByRestaurantAndStatusAndCreatedAtBetween query`

### T-20 — Puerto `RestaurantOwnershipPort` en orders-service
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/domain/port/RestaurantOwnershipPort.java`
- **TDD RED first**: sí — test del puerto
- **Acceptance**: interfaz `Optional<UUID> resolveRestaurantId(Long userId)`. Mismo patrón que en catalog
- **Commit**: `feat(orders): add RestaurantOwnershipPort outbound port`

### T-21 — Adapter HTTP del ownership port en orders-service
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/infrastructure/adapter/outbound/http/HttpRestaurantOwnershipAdapter.java`
- **Acceptance**: llama a `GET http://catalog-service:8082/api/internal/restaurants?userId={userId}` con header. Cache TTL 60s. Maneja 404
- **Commit**: `feat(orders): implement HttpRestaurantOwnershipAdapter`

### T-22 — Use case `GetRestaurantSalesSummaryUseCase`
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/application/usecase/GetRestaurantSalesSummaryUseCase.java`
- **TDD RED first**: sí — `GetRestaurantSalesSummaryUseCaseTest` con dataset controlado
- **Acceptance**: recibe `(userId, restaurantId, range)`. Valida ownership (403 si no). Calcula from/to según range. Query órdenes ENTREGADO en ese rango. Calcula totalOrders, totalRevenue, averageTicket, topProducts (5 más vendidos). Devuelve `SalesSummaryResponse`
- **Commit**: `feat(orders): add GetRestaurantSalesSummaryUseCase with aggregations`

### T-23 — `SalesSummaryController` (o método en OrderController)
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/infrastructure/api/SalesSummaryController.java` (nuevo) o método en `OrderController`
- **TDD RED first**: sí — `SalesSummaryControllerIT`
- **Acceptance**: `GET /api/orders/restaurants/{id}/sales-summary?range=day|week|month`. JWT. Valida rol store_owner (403 si no). Delega al use case. Devuelve `SalesSummaryResponse`
- **Commit**: `feat(orders): add GET /api/orders/restaurants/{id}/sales-summary endpoint`

### T-24 — Tests de agregaciones con dataset controlado
- **Files**: `services/orders-service/src/test/java/cl/flashdrop/orders/application/usecase/GetRestaurantSalesSummaryUseCaseIT.java`
- **Acceptance**: tests con 10 órdenes de prueba (varios productos, varios estados, varios días). Verifica counts, sums, averages, topProducts. Incluye caso "ninguna orden" → totales en cero
- **Commit**: `test(orders): add sales summary aggregation integration tests`

### T-25 — Evaluar índice (opcional)
- **Files**: `services/orders-service/src/main/resources/db/migration/V{n}__idx_orders_restaurant_status_created.sql` (solo si el EXPLAIN lo justifica)
- **Acceptance**: índice `CREATE INDEX IF NOT EXISTS idx_orders_restaurant_status_created ON orders(restaurant_id, status, created_at DESC)`. Solo si PR-orders-metrics lo requiere
- **Commit**: `perf(orders): add composite index for sales summary aggregation` (si aplica)

---

## PR-gateway-2 — `feat/gateway-add-store-routes`

**Branch**: `feat/gateway-add-store-routes` (base: `main` después de los 3 PRs anteriores)
**Dev**: gateway
**Scope**: agregar 2 rutas al config del gateway
**Est. LOC**: ~15 | **Files**: 1

---

### T-26 — Rutas nuevas en `gateway.yaml`
- **Files**: `gateway/config/*.yaml`
- **TDD RED first**: N/A (config)
- **Acceptance**:
  - `GET /api/orders/restaurants/{id}/sales-summary` → `orders-service:8083`
  - `POST/GET/PUT/DELETE /api/catalog/my/products[/{id}]` → `catalog-service:8082`
  - `POST /api/catalog/my/products/image` → `catalog-service:8082`
- **Commit**: `chore(gateway): add store-flow routes (catalog my products and orders sales-summary)`

---

## Orden de merge recomendado

1. `PR-catalog-image` primero (es prerrequisito de `PR-catalog-products`).
2. `PR-catalog-products` después (depende del primero).
3. `PR-orders-metrics` en paralelo desde el inicio (es independiente).
4. `PR-gateway-2` último (depende de los tres).

Los archivos tocados por cada PR son disjuntos entre servicios. Solo dentro de catalog hay dependencia secuencial (image → products).