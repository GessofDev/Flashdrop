# Estrategia de Testing — Features de Repartidor, Tienda y Cliente

**Documento:** Estrategia integral de pruebas (Unitarias, Web/Slice e Integración)  
**Fecha:** 2026-10-05  

---

## 1. Alcance y Taxonomía de Testing

Para mantener una suite confiable, rápida y alineada con Clean Architecture y las normas del proyecto, las pruebas se dividen estrictamente en tres categorías:

1. **Pruebas Unitarias Puras (Unit Tests)**:
   - **Regla:** Corren en memoria, sin Docker, sin red, sin Spring Context y **sin base de datos**.
   - **Objetivo:** Lógica de negocio de dominio, casos de uso (orquestación con mocks de puertos outbound), mappers, políticas de autorización y value objects. Ejecución en milisegundos en cada build.
2. **Pruebas de Capa Web / Slice (Controller / Web Tests)**:
   - **Regla:** Prueban el adaptador inbound HTTP (`MockMvc` standalone o slice web).
   - **Objetivo:** Códigos de estado HTTP, validación de schemas de entrada (Jakarta Validation), deserialización, headers de seguridad y traducción de excepciones en `GlobalExceptionHandler`. Casos de uso se sustituyen con Mockito.
3. **Pruebas de Integración (`*IT.java` / Integration Tests)**:
   - **Regla:** Levantan contexto Spring completo y dependencias de infraestructura reales (Testcontainers Postgres, MockServer/WireMock para llamadas inter-servicio, emuladores S3 o gateway proxy).
   - **Objetivo:** Auditoría de queries JPA, constraints de base de datos (`@PreUpdate`, claves únicas), integración de seguridad Spring Security (JWT / JWKS) y passthrough de streams en el gateway.
4. **Pruebas de Regresión**:
   - **Regla:** Suites existentes en servicios que no sufren cambios de código en este ciclo (ej. `delivery-service`).

---

## 2. Pruebas Unitarias Puras (Nivel 1 — En memoria, sin I/O ni DB)

### 2.1 `auth-service`

| Prueba | Ubicación / Clase | Qué valida | Mocks requeridos | Estado |
|---|---|---|---|---|
| `UserTest` | `UserTest.java` | Método inmutable de dominio `User.conPerfil`: valida que modifica únicamente `name`, `lastName`, `phone` y `photo`, preservando intactos `id`, `email`, `rut`, `roles` y `createdAt`. | Ninguno (POJO puro) | Mergeado en `main` (#41) |
| `UpdateUserProfileServiceTest` | `UpdateUserProfileServiceTest.java` | Orquestación del caso de uso: actualización exitosa, lanzamiento de `UserNotFoundException` (404) si el usuario no existe, y propagación de conflicto (409) ante colisión de teléfono. | `UserRepositoryPort` (Mockito) | Mergeado en `main` (#41) |

---

### 2.2 `orders-service`

| Prueba | Ubicación / Clase | Qué valida | Mocks requeridos | Estado |
|---|---|---|---|---|
| `OrderDomainTest` | `OrderDomainTest.java` | Matriz completa de transiciones de estado `from × to` en `Order.validateStatusTransition()`. Valida transiciones válidas (`NUEVO_PEDIDO → PREPARANDO → LISTO_PARA_RETIRO → RETIRADO → ENTREGADO`), caminos legacy (`EN_CAMINO`), y rechazo con `OrderDomainException` para transiciones prohibidas o desde `ENTREGADO`. | Ninguno (Dominio puro) | Implementado en rama `feat/orders-jwt-roles` |
| `RoleTransitionPolicyTest` | `RoleTransitionPolicyTest.java` | Valida matriz de permisos por rol de negocio (`Repartidor`, `Restaurante`) para cambios de estado de pedidos. | Ninguno (Dominio puro) | Implementado en rama `feat/orders-jwt-roles` |
| `UpdateOrderStatusUseCaseTest` | `UpdateOrderStatusUseCaseTest.java` | Flujo del caso de uso: autorización por rol, validación de ownership de restaurante para rol `Restaurante` (403 IDOR si intenta cambiar orden ajena), validación de transición y guardado en repositorio. | `OrderRepositoryPort`, `RestaurantOwnershipPort` (Mockito) | Implementado en rama `feat/orders-jwt-roles` |
| `ListAvailableOrdersUseCaseTest` | `ListAvailableOrdersUseCaseTest.java` | Filtro de órdenes en estado `LISTO_PARA_RETIRO` sin repartidor asignado, orden FIFO por `createdAt`, límites (1–50, default 5) y enriquecimiento de datos de cliente. | `OrderRepositoryPort`, `ClientRepositoryPort` (Mockito) | Implementado en rama `feat/orders-jwt-roles` |
| `GetRestaurantSalesSummaryUseCaseTest` | `GetRestaurantSalesSummaryUseCaseTest.java` | Cálculos de métricas por rango (`day`, `week`, `month`): cálculo de ventanas temporales, total de órdenes entregadas, ingresos totales, ticket promedio y cálculo de top 5 productos más vendidos. Caso de 0 órdenes. | `OrderRepositoryPort`, `RestaurantOwnershipPort` (Mockito) | Implementado en rama `feat/orders-jwt-roles` |
| `ClaimDeliveryOrdersUseCaseTest` | `ClaimDeliveryOrdersUseCaseTest.java` | Verifica que el reclamo de pedidos asigna el repartidor pero **NO muta** el estado de la orden a `EN_CAMINO`, dejándola en `LISTO_PARA_RETIRO`. | `OrderRepositoryPort`, `DeliveryPort` (Mockito) | Implementado en rama `feat/orders-jwt-roles` |
| `GlobalExceptionHandlerTest` | `GlobalExceptionHandlerTest.java` | Mapeo determinista de excepciones a códigos HTTP y envelope `ApiError`: 400 (argumentos inválidos), 403 (violación de ownership/rol), 404 (orden no encontrada), 409 (transición inválida). | Ninguno | Mergeado en `main` |

---

### 2.3 `catalog-service`

| Prueba | Ubicación / Clase | Qué valida | Mocks requeridos | Estado |
|---|---|---|---|---|
| `OwnerProductUseCasesTest` | `OwnerProductUseCasesTest.java` | Casos de uso owner CRUD: `OwnerCreateProductUseCase` (deriva `restaurantId` del usuario autenticado), `OwnerListProductsUseCase` (lista productos propios incluyendo inactivos), `OwnerUpdateProductUseCase` (bloquea mutación de `restaurantId` y 403 IDOR), `OwnerDeleteProductUseCase` (soft-delete `available=false`). | `ProductRepositoryPort`, `GetRestaurantByUserIdUseCase` (Mockito) | En rama `codex/catalog-store-security-images` |
| `UploadProductImageUseCaseTest` | `UploadProductImageUseCaseTest.java` | Validación estricta de subida: formato MIME permitido (`jpeg`, `png`, `webp`), tamaño máximo (<= 5MB, probando límite exacto), rechazo con excepción 400/413 y delegación al storage outbound. | `ProductImageStorage` (Mockito) | En rama `codex/catalog-store-security-images` |
| `JwtAuthoritiesMapperTest` | `JwtAuthoritiesMapperTest.java` | Mapeo de claims `roles[]` del JWT RS256 a authorities de Spring Security (`ROLE_Restaurante`, `ROLE_Cliente`). Lógica encapsulada en `SecurityConfig.jwtAuthenticationConverter()`, probada en aislamiento unitario sin levantar contexto Spring. Manejo de token sin claims o roles vacíos. | Ninguno (POJO / unitario) | Pendiente de implementar |
| `ProductImageUrlResolverTest` | `ProductImageUrlResolverTest.java` | Resolución de URLs de imagen estables a partir de object keys de S3 (`products/{yyyy}/{mm}/{uuid}.ext`). | Ninguno | En rama `codex/catalog-store-security-images` |

---

### 2.4 `gateway`

| Prueba | Ubicación / Clase | Qué valida | Mocks requeridos | Estado |
|---|---|---|---|---|
| `MultipartParserTest` | `multipart-parser.test.ts` | Configuración de `@fastify/multipart`: aceptación de payloads hasta 6MB y rechazo inmediato con código 413 cuando el stream supera el límite antes de reenviar al proxy. | Fastify instancia en memoria | Pendiente de código (T-30) |
| `ProxyMultipartStreamTest` | `proxy-multipart.test.ts` | Valida que `engine.ts` realiza passthrough del stream raw sin aplicar `JSON.stringify`, preservando cabecera `Content-Type` intacta con su boundary y `Content-Length`. | HTTP mock handler | Pendiente de código (T-31) |

---

## 3. Pruebas de Capa Web / Slice (Nivel 2 — Contrato HTTP, MockMvc, Handlers)

### 3.1 `auth-service`

| Prueba | Ubicación / Clase | Qué valida | Mocks requeridos | Estado |
|---|---|---|---|---|
| `AuthControllerTest` | `AuthControllerTest.java` | Contrato HTTP de `PUT /auth/profile`: respuesta 200 con `UserProfile`, 401 si falta header `Authorization`, 404 si el usuario no existe, 409 si el teléfono colisiona, y 400 ante payload no-JSON. Valida que el email en el body no sea procesado. | Casos de uso de Auth (Mockito) | Mergeado en `main` (#41) |

---

### 3.2 `orders-service`

| Prueba | Ubicación / Clase | Qué valida | Mocks requeridos | Estado |
|---|---|---|---|---|
| `AvailableDeliveryOrdersControllerTest` | `AvailableDeliveryOrdersControllerTest.java` | `GET /api/orders/available-for-delivery`: conversión de parámetro `restaurant_id` (Long a UUID vía `IdConverter`), paginación/límite por query params, códigos 200 y 400 si falta parámetro obligatorio. Incluye casos de borde para 403 por rol, usuario con múltiples roles y bad request ante ID no numérico. | `ListAvailableOrdersUseCase` (Mockito) | Rehecha con use case mockeado (7 pasan) |
| `SalesSummaryControllerTest` | `SalesSummaryControllerTest.java` | `GET /api/orders/restaurants/{id}/sales-summary`: parsing de rango temporal (`day`/`week`/`month`), conversión de ID y retorno de envelope de métricas. Incluye casos de borde para 403 por rol, usuario con múltiples roles y bad request ante ID no numérico. | `GetRestaurantSalesSummaryUseCase` (Mockito) | Rehecha con use case mockeado (9 pasan) |
| `OrderControllerStatusAuthzTest` | `OrderControllerStatusAuthzTest.java` | Slice web con MockMvc para `PUT /api/orders/{id}/status`: verifica matriz de códigos de estado HTTP ante roles en token JWT, retorno 200 ante transición autorizada y 403/409 delegados por handlers. | `UpdateOrderStatusUseCase` (Mockito) | Implementado en rama `feat/orders-jwt-roles` |

---

### 3.3 `catalog-service`

| Prueba | Ubicación / Clase | Qué valida | Mocks requeridos | Estado |
|---|---|---|---|---|
| `ProductImageControllerTest` | `ProductImageControllerTest.java` | Slice web `POST /api/catalog/my/products/image`: recepción de `MultipartFile`, respuesta 201 con `{objectKey, url}`, 400 por MIME no soportado, 413 si excede tamaño y 502 ante falla del storage. **Regla:** Caso de uso `UploadProductImageUseCase` mockeado; no levanta SpringBoot completo ni base de datos. | `UploadProductImageUseCase` (Mockito) | Pendiente de desacoplar de contexto Spring completo |
| `PublicCatalogControllerTest` | `PublicCatalogControllerTest.java` | Slice web: verifica que `GET /catalog/products` y endpoints públicos utilicen las consultas que filtran `is_available = true` para no exponer ítems desactivados al cliente. **Regla:** Caso de uso `ListProductsUseCase` mockeado. | `ListProductsUseCase` (Mockito) | Pendiente de desacoplar de contexto Spring completo |

---

## 4. Pruebas de Integración (Nivel 3 — Testcontainers, Base de Datos, Red, E2E)

### 4.1 `auth-service`

| Prueba | Ubicación / Clase | Qué valida | Entorno / Dependencias | Estado |
|---|---|---|---|---|
| `AuthPostgresIntegrationTest` | `AuthPostgresIntegrationTest.java` | Persistencia real en Postgres: ejecución de `PUT /auth/profile`, verificación de que el hook `@PreUpdate` actualiza efectivamente la columna `updated_at` en base de datos, y verificación de constraint unique en `phone` arrojando 409 `RESOURCE_ALREADY_EXISTS`. | Testcontainers PostgreSQL | Mergeado en `main` (#41) |

---

### 4.2 `orders-service`

| Prueba | Ubicación / Clase | Qué valida | Entorno / Dependencias | Estado |
|---|---|---|---|---|
| `OrderControllerStatusAuthzIT` | `OrderControllerStatusAuthzIT.java` | Matriz 2D de integración: rol del JWT (`Repartidor`, `Restaurante`) × transición de estado en `PUT /api/orders/{id}/status`. Valida 200 (permitido), 403 (rol no habilitado o IDOR de tienda ajena) y 409 (transición de negocio inválida). | Contexto Spring + JWT Security Mock | Implementado en rama `feat/orders-jwt-roles` |
| `AvailableDeliveryOrdersIT` | `AvailableDeliveryOrdersIT.java` | Integración de consulta JPA `findAvailableForDelivery`: verifica que órdenes asignadas a repartidor o en estados distintos a `LISTO_PARA_RETIRO` sean excluidas en la base de datos real. | Testcontainers PostgreSQL / H2 | Implementado en rama `feat/orders-jwt-roles` |
| `RestaurantMetricsIT` | `RestaurantMetricsIT.java` | Ejecución de queries agregadas de ventas (`findByRestaurantAndStatusAndCreatedAtBetween`): sumas de montos, conteo de órdenes y agrupación de top productos con dataset controlado. | Testcontainers PostgreSQL / H2 | Implementado en rama `feat/orders-jwt-roles` |
| `CatalogHttpClientAdapterTest` | `CatalogHttpClientAdapterTest.java` | Contrato HTTP saliente hacia `catalog-service` (`GET /api/internal/restaurants?userId=...`): serialización de header `X-Internal-Api-Key` y deserialización de un único objeto de respuesta (corrección del contrato de lista a objeto). | WireMock / MockWebServer | Mergeado en `main` |

---

### 4.3 `catalog-service`

| Prueba | Ubicación / Clase | Qué valida | Entorno / Dependencias | Estado |
|---|---|---|---|---|
| `MyStoreProductControllerIT` | `MyStoreProductControllerIT.java` | CRUD completo `/api/catalog/my/products`: autenticación JWT RS256 contra JWKS, autorización con rol `Restaurante` (403 si falta rol, 401 sin token), protección contra IDOR (intento de editar o borrar producto de otro restaurante devuelve 403). | Contexto Spring + Testcontainers Postgres | En rama `codex/catalog-store-security-images` |
| `ProductImageControllerIT` | `ProductImageControllerIT.java` | Integración web completa con Spring Boot context: valida pipeline de seguridad `@EnableMethodSecurity`, autenticación JWT real/mockeada con filtros y wiring de controladores. | `@SpringBootTest` + `@AutoConfigureMockMvc` | En rama `codex/catalog-store-security-images` (renombrado desde `*Test`) |
| `S3ProductImageStorageTest` | `S3ProductImageStorageTest.java` | Integración del adaptador outbound con cliente AWS S3: subida de bytes, generación de object key estructurada (`products/{yyyy}/{mm}/{uuid}.ext`), propagación de excepciones `ImageStorageException` ante fallo del bucket. | LocalStack S3 / Mock S3Client | En rama `codex/catalog-store-security-images` |
| `JpaProductRepositoryAdapterTest` | `JpaProductRepositoryAdapterTest.java` | Verificación de queries JPA derivadas: `findAllByIsAvailableTrue`, `findByCategoryIdAndIsAvailableTrue` y `findByRestaurantIdAndIsAvailableTrue` filtrando registros activos. | Testcontainers PostgreSQL / H2 | Pendiente de implementar |

---

### 4.4 `gateway`

| Prueba | Ubicación / Clase | Qué valida | Entorno / Dependencias | Estado |
|---|---|---|---|---|
| `GatewayMultipartIT` | `multipart-upload.test.ts` | Test end-to-end de subida multipart: envío de archivo representativo (~4MB) atravesando el gateway hacia `/api/catalog/my/products/image`. Valida passthrough sin corrupción de bytes, headers intactos y código 201. | Gateway Fastify + Mock Backend HTTP | Pendiente de implementar (T-33) |

---

## 5. Pruebas de Regresión (Servicios sin cambios de código)

### 5.1 `delivery-service`

- **Alcance:** Conforme a la auditoría técnica del diseño, `delivery-service` **no sufre modificaciones de código**.
- **Suite de Regresión:** Se ejecuta la suite completa de pruebas unitarias existentes (las 22 clases `*Test`, 123 pruebas que corren en memoria sin Docker ni base de datos) para certificar que el endpoint de claim y la asignación de rutas no presentan efectos secundarios. Las pruebas de integración (`*IT`) se delegan al pipeline de CI.

---

## 6. Resumen de Componentes Eliminados y Erratas Corregidas

Para evitar divergencias históricas respecto a versiones preliminares del plan:

1. **Eliminado: `RestaurantOwnershipResolverTest` (Catalog):**
   - *Motivo:* El componente con HTTP self-call y cache Caffeine fue descartado. Catalog resuelve la pertenencia de forma local con `GetRestaurantByUserIdUseCase`.
2. **Corrección de Errata en Auth IT:**
   - *Motivo:* El plan original mencionaba "email duplicado" en `PUT /auth/profile`. El email es inmutable en este endpoint; el caso de colisión de unicidad de negocio corresponde a **teléfono duplicado** (409 `RESOURCE_ALREADY_EXISTS`).
3. **Consolidación en Auth:**
   - *Motivo:* No se crea `AuthControllerIT` ni `UserEntityTest` aislados; la validación con contenedor real y el test de `@PreUpdate` en `updated_at` fueron absorbidos por `AuthPostgresIntegrationTest` para optimizar el pipeline de CI.
4. **Incorporación Formal de Gateway:**
   - *Motivo:* Se añaden formalmente las pruebas T-30, T-31 y T-33 al plan de testing dado que el gateway incorpora código Fastify para manejo de streams multipart de 6MB.
5. **Alineación de Taxonomía y Aislamiento (Feedback QA / Felipe - Octubre 2026):**
   - *Catalog:* Se separa la prueba de contrato slice (`ProductImageControllerTest`, Nivel 2 con MockMvc y use case mockeado) de la prueba de contexto completo (`ProductImageControllerIT`, Nivel 3 con `@SpringBootTest`). Se aclara que el mapeo de roles JWT vive en `SecurityConfig.jwtAuthenticationConverter()`.
   - *Orders:* Se formaliza el mock de `ListAvailableOrdersUseCase` y `GetRestaurantSalesSummaryUseCase` en las pruebas de controller, conservando los casos de borde de roles y tipos de datos. Se corrige la matriz de transiciones descartando el rol inexistente `admin`.
   - *Delivery:* Se restringe la regresión local a las 22 clases unitarias (`*Test`), excluyendo requisitos de Docker en local.
   - *Gateway:* Se explicita la precondición de implementación de T-30 y T-31 antes de ejecutar las pruebas unitarias de multipart.
