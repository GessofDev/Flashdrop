# Tasks: `profile-and-delivery`

## PR Chain

**Strategy**: parallel-by-owner (cada dev dueño de su servicio mergea su PR en cualquier orden; `PR-gateway-1` espera al final).

```
main
  ├── feat/auth-update-profile                       (PR-auth)
  ├── feat/orders-status-authz                        (PR-orders-status-authz)
  ├── feat/orders-available-for-delivery              (PR-orders-available)
  ├── feat/orders-claim-no-status-mutation            (PR-orders-claim)
  └── feat/gateway-add-available-for-delivery         (PR-gateway-1, último)
```

---

## PR-auth — `feat/auth-update-profile`

**Branch**: `feat/auth-update-profile` (base: `main`)
**Dev**: auth (Nicolás Leiva)
**Scope**: `PUT /auth/profile` con use case + DTO + controller + SecurityConfig + User domain method + UserEntity @PreUpdate + tests
**Est. LOC**: ~180 | **Files**: ~8

> **Feedback aplicado** (Nicolás Leiva, 2026-09-24, contra `main @ afc8f0a`):
> 1. `SecurityConfig.java` debe permitir `PUT /auth/profile` explícitamente (si no, `anyRequest().denyAll()` → 403 silencioso).
> 2. No existe `JwtAuthFilter` en auth-service; el patrón es `validateToken.validate(bearer(authorization))` en el controller.
> 3. `User` es inmutable (`private final` en todos los campos); `UserRepository.save(...)` reescribe todas las columnas. Sin método de dominio, construir un `User` solo con 4 campos editables tira `InvalidUserException` porque `email` queda `null`.
> 4. `users.updated_at` (V1 línea 24) existe pero ni `UserEntity` la mapea ni hay trigger; sin `@PreUpdate` la columna nunca se actualiza. Fix sin migración.
> 5. Path real = `/auth/profile` (gateway.yaml prefijo `/auth`), no `/api/auth/profile`.

---

### T-1 — DTO `UpdateProfileRequest`
- **Files**: `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/dto/UpdateProfileRequest.java`
- **TDD RED first**: sí — definir el DTO con validaciones Jakarta Validation (`@NotBlank`, `@Size`, `@Pattern` donde aplique)
- **Acceptance**: record con `name`, `lastName`, `phone`, `photo`. Validaciones activas. Rechaza campos extra (`@JsonIgnoreProperties(ignoreUnknown = false)`). **No incluye `email` ni `rut`** (no editables vía este endpoint)
- **Commit**: `feat(auth): add UpdateProfileRequest DTO with strict validation`

### T-2 — Método de dominio `User.conPerfil`
- **Files**: `services/auth-service/src/main/java/com/flashdrop/auth/domain/model/User.java`
- **TDD RED first**: sí — `UserTest` cubre que el método retorna nueva instancia con los campos solicitados y preserva `id, email, rut, roles, createdAt`
- **Acceptance**: nuevo método `public User conPerfil(String name, String lastName, String phone, String photo)` que retorna `new User(this.id, this.email, this.rut, name, lastName, phone, photo, this.roles, this.createdAt)`. **No** modifica la instancia actual (la clase es inmutable)
- **Commit**: `feat(auth): add User.conPerfil domain method preserving email rut roles createdAt`

### T-3 — Use case `UpdateUserProfileUseCase`
- **Files**:
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/port/inbound/UpdateUserProfileUseCase.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/usecase/UpdateUserProfileService.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/dto/UpdateUserProfileCommand.java`
- **TDD RED first**: sí — `UpdateUserProfileServiceTest` antes de implementar
- **Acceptance**: el use case recibe `(userId, command)`, hace `users.findById(userId)` (lanza `UserNotFoundException` → 404 si no existe), aplica `userExistente.conPerfil(command.name(), command.lastName(), command.phone(), command.photo())` (ver T-2), llama `users.save(userModificado)` que dispara `DataIntegrityViolationException` → 409 `RESOURCE_ALREADY_EXISTS` si el phone choca con el de otro user (manejado por `GlobalExceptionHandler.handleConflictoDeDatos`). Devuelve `UserProfile` actualizado
- **Commit**: `feat(auth): add UpdateUserProfileUseCase with phone collision via DataIntegrityViolation`

### T-4 — `PUT /auth/profile` en `AuthController`
- **Files**: `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/AuthController.java`
- **TDD RED first**: sí — `AuthControllerTest` con MockMvc verifica 200, 400, 401, 404, 409
- **Acceptance**: handler `profilePut(...)` con `@PutMapping("/profile")` que extrae `userId` del JWT usando el patrón existente `validateToken.validate(bearer(authorization))` (mismo helper privado `bearer()` que el GET /auth/profile; **NO** crear `JwtAuthFilter` — no existe en este servicio). Llama al use case con `claims.userId()`, devuelve `UserProfile` envuelto en `ApiResponse`. Sin parámetros de `userId` en el body
- **Commit**: `feat(auth): add PUT /auth/profile endpoint using existing validateToken pattern`

### T-5 — `SecurityConfig`: permitir `PUT /auth/profile`
- **Files**: `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/config/SecurityConfig.java`
- **TDD RED first**: N/A (config). Cubierto indirectamente por `AuthControllerIT` (T-8): un test que espera 200 con JWT válido devolvería 403 si esta línea falta.
- **Acceptance**: agregar `.requestMatchers(HttpMethod.PUT, "/auth/profile").permitAll()` a la cadena, junto al `requestMatchers(HttpMethod.GET, "/auth/validate", "/auth/profile", ...)`. Sin esto el PUT cae en `anyRequest().denyAll()` y devuelve 403 silencioso sin pasar por `GlobalExceptionHandler`. El `permitAll` es correcto: la auth real la hace el controller con `validateToken.validate(...)`, no Spring Security
- **Commit**: `fix(auth): permit PUT /auth/profile in SecurityConfig chain`

### T-6 — Registrar use case en configuración
- **Files**: `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/config/UseCaseConfiguration.java`
- **TDD RED first**: N/A (wiring)
- **Acceptance**: `@Bean` para `UpdateUserProfileUseCase`. La app arranca sin errores
- **Commit**: `chore(auth): register UpdateUserProfileUseCase in config`

### T-7 — `UserEntity` `@PreUpdate` para `updated_at`
- **Files**: `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/outbound/persistence/jpa/entity/UserEntity.java`
- **TDD RED first**: sí — `UserEntityTest` con `@DataJpaTest` verifica que después de `save(...)` sobre una entidad existente, la columna `updated_at` cambió respecto al valor inicial
- **Acceptance**: agregar campo `private Instant updatedAt;` mapeado con `@Column(name = "updated_at")` (sin `insertable = false` ni `updatable = false` — necesitamos que JPA lo escriba en update). El valor inicial del INSERT lo provee el `default now()` de Postgres (V1 línea 24). Agregar método `@PreUpdate protected void onUpdate() { this.updatedAt = Instant.now(); }`. **Sin migración Flyway** — la columna ya existe
- **Commit**: `fix(auth): map users.updated_at and update it on @PreUpdate`

### T-8 — Tests de integration `AuthControllerIT`
- **Files**: `services/auth-service/src/test/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/AuthControllerIT.java`
- **TDD RED first**: el archivo puede crearse con el test RED junto a T-4
- **Acceptance**: tests con Testcontainers + JWT firmado. Casos: perfil válido (200), phone colisiona (409 `RESOURCE_ALREADY_EXISTS`), JWT inválido (401), body con email (rechazado por strict schema → 400), userId inexistente (404). **El caso 409 es el más frágil** — depende de que `GlobalExceptionHandler.handleConflictoDeDatos` siga mapeando `DataIntegrityViolationException` a 409 `RESOURCE_ALREADY_EXISTS`. Si alguien refactoriza ese handler, este test es lo que sostiene el comportamiento
- **Commit**: `test(auth): add PUT /auth/profile integration tests with phone-collision case`

---

## PR-orders-status-authz — `feat/orders-status-authz`

**Branch**: `feat/orders-status-authz` (base: `main`)
**Dev**: orders
**Scope**: matriz rol→transición en `PUT /api/orders/{id}/status`
**Est. LOC**: ~180 | **Files**: ~6

---

### T-6 — Enum o record `RoleTransitionPolicy`
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/domain/model/RoleTransitionPolicy.java`
- **TDD RED first**: sí — test del policy primero
- **Acceptance**: clase con un mapa inmutable `Map<String, Map<OrderStatus, Set<OrderStatus>>>` que codifica la matriz del FR-4. Método `boolean isAllowed(String role, OrderStatus from, OrderStatus to)`
- **Commit**: `feat(orders): add RoleTransitionPolicy with Repartidor Restaurante admin matrix`

### T-7 — Excepción `AccessDeniedException` reusada o envuelta
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/domain/exception/StatusTransitionForbiddenException.java`
- **TDD RED first**: N/A (excepción simple)
- **Acceptance**: nueva excepción específica para distinguir "rol no autorizado" de "transición inválida por estado". El handler global la mapea a 403
- **Commit**: `feat(orders): add StatusTransitionForbiddenException for role authz failures`

### T-8 — Modificar `UpdateOrderStatusUseCase` para usar el policy
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/application/usecase/UpdateOrderStatusUseCase.java`
- **TDD RED first**: el test del policy (T-6) ya cubre la lógica. Agregar test de integración que prueba el flow completo
- **Acceptance**: el use case recibe `(orderId, newStatus, currentRole)`. Lee el pedido actual, valida `currentRole` contra el policy (`StatusTransitionForbiddenException` si falla), valida transición de estado (`OrderDomainException` si falla), guarda
- **Commit**: `feat(orders): enforce role-based transition policy in UpdateOrderStatusUseCase`

### T-9 — Modificar `OrderController.updateOrderStatus` para pasar rol
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/infrastructure/api/OrderController.java`
- **TDD RED first**: sí — `OrderControllerStatusAuthzIT` antes de este cambio
- **Acceptance**: el handler extrae el rol del JWT (`currentUserResolver.requireCurrentRole()` o equivalente nuevo) y lo pasa al use case
- **Commit**: `feat(orders): pass JWT role to UpdateOrderStatusUseCase`

### T-10 — `OrderControllerStatusAuthzIT`
- **Files**: `services/orders-service/src/test/java/cl/flashdrop/orders/infrastructure/api/OrderControllerStatusAuthzIT.java`
- **TDD RED first**: sí — antes de cualquier cambio en el controller
- **Acceptance**: matriz 2D (`Repartidor`, `Restaurante`, `admin`) × (transición válida, transición inválida por estado, transición prohibida por rol). Mínimo 8 casos. Cubre los códigos 200, 403, 409
- **Commit**: `test(orders): add role-based status transition integration tests`

### T-11 — Matrix completa `from × to` en `Order.validateStatusTransition()` (NUEVO)
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/domain/model/Order.java`
- **TDD RED first**: sí — test unitario exhaustivo
- **Acceptance**: la implementación actual solo rechaza `ENTREGADO → *`. Agregar matrix completa (ver spec.md FR-4):
  - Válidas: `NUEVO_PEDIDO → PREPARANDO`, `NUEVO_PEDIDO → LISTO_PARA_RETIRO`, `PREPARANDO → LISTO_PARA_RETIRO`, `LISTO_PARA_RETIRO → RETIRADO`, `RETIRADO → ENTREGADO`
  - Legacy (permitidas pero deprecated): `LISTO_PARA_RETIRO → EN_CAMINO`, `RETIRADO → EN_CAMINO`, `EN_CAMINO → RETIRADO`, `EN_CAMINO → ENTREGADO`
  - Rechazadas (409): `ENTREGADO → *` y cualquier otra no listada
- **Commit**: `feat(orders): add complete from-to matrix in Order.validateStatusTransition`

### T-12 — Validación de ownership para `Restaurante` en `UpdateOrderStatusUseCase` (NUEVO)
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/application/usecase/UpdateOrderStatusUseCase.java` + `infrastructure/adapter/outbound/http/HttpRestaurantOwnershipAdapter.java` (adaptador que llama a `catalog-service:8082/api/internal/restaurants?userId=...`)
- **TDD RED first**: sí — test IT del caso IDOR
- **Acceptance**: cuando `currentUserRole == "Restaurante"`, validar que `order.getRestaurantId() == ownershipPort.resolveRestaurantId(currentUserId)` (llamada HTTP a catalog). Si no coincide, lanzar `AccessDeniedException` (403). El orden de validación en el use case es: ownership (403) → rol/policy (403) → transición (409)
- **Commit**: `feat(orders): validate restaurant ownership for Restaurante role in updateOrderStatus`

### T-13 — Actualizar `openapi.yaml` con los nuevos endpoints (NUEVO)
- **Files**: `services/orders-service/openapi.yaml`
- **TDD RED first**: no
- **Acceptance**: documentar los 3 endpoints agregados: `PUT /api/orders/{id}/status` con matriz rol × transición, `GET /api/orders/available-for-delivery`, `GET /api/orders/restaurants/{id}/sales-summary`. El OpenAPI debe actualizarse en el mismo PR (no queda drift con la implementación)
- **Commit**: `docs(openapi): update openapi.yaml with new orders endpoints`

---

## PR-orders-available — `feat/orders-available-for-delivery`

**Branch**: `feat/orders-available-for-delivery` (base: `main`)
**Dev**: orders
**Scope**: `GET /api/orders/available-for-delivery`
**Est. LOC**: ~150 | **Files**: ~5

---

### T-11 — Query method en `OrderRepositoryPort`
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/domain/port/OrderRepositoryPort.java` + implementación JPA
- **TDD RED first**: sí — test del repositorio con H2 o similar
- **Acceptance**: nuevo método `List<Order> findAvailableForDelivery(UUID restaurantId, int limit)` que filtra por `restaurantId`, `status = LISTO_PARA_RETIRO`, ordenado por `createdAt ASC`, limitado
- **Commit**: `feat(orders): add findAvailableForDelivery query to OrderRepositoryPort`

### T-12 — Use case `ListAvailableOrdersUseCase`
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/application/usecase/ListAvailableOrdersUseCase.java`
- **TDD RED first**: sí — `ListAvailableOrdersUseCaseTest` con repo mockeado
- **Acceptance**: valida `restaurantId` no nulo, valida `limit` en rango 1–50, llama al repo, enriquece con `OrderEnricher`, devuelve `List<Order>`
- **Commit**: `feat(orders): add ListAvailableOrdersUseCase with limit validation`

### T-13 — Endpoint `GET /api/orders/available-for-delivery`
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/infrastructure/api/AvailableDeliveryOrdersController.java` (nuevo) o método nuevo en `OrderController`
- **TDD RED first**: sí — `AvailableDeliveryOrdersIT` antes
- **Acceptance**: handler con `@RequestParam Long restaurant_id` (requerido), `@RequestParam(defaultValue = "5") Integer limit`. Auth JWT. Devuelve `[OrderListResponse]`. 400 si falta `restaurant_id`, 403 si rol no es delivery, 400 si limit fuera de rango
- **Commit**: `feat(orders): add GET /api/orders/available-for-delivery endpoint`

### T-14 — Tests `AvailableDeliveryOrdersIT`
- **Files**: `services/orders-service/src/test/java/cl/flashdrop/orders/infrastructure/api/AvailableDeliveryOrdersIT.java`
- **Acceptance**: casos: happy path con 5 pedidos (devuelve 5), happy path con 3 pedidos (devuelve 3), filtro por estado (no devuelve NUEVO_PEDIDO ni PREPARANDO), límite (no devuelve más de N), 403 si rol no es `Repartidor`, 400 si falta restaurant_id, 400 si limit > 50
- **Commit**: `test(orders): add available-for-delivery integration tests`

---

## PR-orders-claim (ex PR-delivery) — `feat/orders-claim-no-status-mutation`

**Branch**: `feat/orders-claim-no-status-mutation` (base: `main`)
**Dev**: orders (`delivery-service` tiene 0 cambios de código; la mutación de claim reside en `orders-service`)
**Scope**: `claim` en `orders-service` deja de mutar `Order.status` y ruta a `EN_CAMINO`
**Est. LOC**: ~60 | **Files**: ~3

---

### T-15 — Eliminar `Order.assignDelivery()` y su test obsoleto (reemplaza "confirmar código muerto")
- **Files**: 
  - `services/orders-service/src/main/java/cl/flashdrop/orders/domain/model/Order.java` (eliminar método `assignDelivery`)
  - `services/orders-service/src/test/java/cl/flashdrop/orders/domain/model/OrderDomainTest.java` (eliminar test `shouldAssignDeliveryAndChangeStatusToEnCamino` línea 86)
- **TDD RED first**: sí — primero verificar que el test obsoleto está activo, luego eliminar método y test atómicamente
- **Acceptance**: 
  1. `Order.assignDelivery(UUID deliveryId)` es código muerto (grep confirma que no se llama desde ningún flujo de producción). Pero existe un test activo (`OrderDomainTest.shouldAssignDeliveryAndChangeStatusToEnCamino` línea 86) que valida el comportamiento obsoleto de mutar status a `EN_CAMINO`. **Dejar ambos es deuda técnica peligrosa** — el método valida un contrato que el spec viola, y el test pasa pero por razones obsoletas.
  2. **Eliminar el método `Order.assignDelivery()`** completo (incluye el bloque que cambia `this.status = OrderStatus.EN_CAMINO`). No hay callers en producción (verificado por grep `services/.*\.java` → 0 hits fuera del test).
  3. **Eliminar el test `shouldAssignDeliveryAndChangeStatusToEnCamino`** de `OrderDomainTest.java`. El comportamiento que validaba (mutar status a `EN_CAMINO`) ya no es parte del contrato.
  4. Después de la eliminación, `OrderDomainTest` debe pasar con todos los tests restantes.
- **Commit**: `refactor(orders): remove dead Order.assignDelivery method and its obsolete test`

### T-16 — Quitar mutación de status en `ClaimDeliveryOrdersUseCase.execute()` (orders-service)
- **Files**: `services/orders-service/src/main/java/cl/flashdrop/orders/application/usecase/ClaimDeliveryOrdersUseCase.java`
- **TDD RED first**: sí — actualizar `ClaimDeliveryOrdersUseCaseTest` para verificar que `Order.status` no se modifica post-claim
- **Acceptance**: eliminar las llamadas en línea 91 (`orderRepository.claimOrders(uniqueOrderIds, deliveryId, OrderStatus.EN_CAMINO)`) y línea 98 (`deliveryPort.updateRouteStatus(uniqueOrderIds, OrderStatus.EN_CAMINO.getValue())`). El use case solo persiste `deliveryId` y deja el estado como está (`LISTO_PARA_RETIRO`). **Coordinar con dev de delivery antes de merge**: en `delivery-service`, el flujo ya deja la ruta en `ASSIGNED` y delega a `orders-service`
- **Commit**: `feat(orders): remove Order status mutation from ClaimDeliveryOrdersUseCase`

### T-17 — Test explícito del nuevo comportamiento
- **Files**: `services/orders-service/src/test/java/cl/flashdrop/orders/application/usecase/ClaimDeliveryOrdersUseCaseTest.java`
- **Acceptance**: tests que mockean el repo y verifican que después de `execute(userId, orderIds)`, `orderRepository.claimOrders(...)` **no se llama** con `EN_CAMINO`, y `deliveryPort.updateRouteStatus(...)` **no se llama** con `EN_CAMINO`. El `Order` queda con `status = LISTO_PARA_RETIRO` y `deliveryId` poblado
- **Commit**: `test(orders): verify ClaimDeliveryOrdersUseCase does not mutate Order status`

### T-17b — Documentar endpoint legacy `POST /api/delivery/claim` (NUEVO)
- **Files**: `services/orders-service/openapi.yaml` línea 190 (`/api/delivery/claim`)
- **TDD RED first**: no
- **Acceptance**: agregar NOTA en el OpenAPI documentando que el endpoint legacy `POST /api/delivery/claim` (que llama a `/api/internal/orders/claim` en orders-service) **también deja de mutar status** después de este PR. El comportamiento cambia: antes `claim` transicionaba a `EN_CAMINO`, ahora solo persiste `deliveryId`. El frontend Flutter debe transicionar manualmente al primer `RETIRADO`
- **Commit**: `docs(openapi): document /api/delivery/claim no longer mutates status`

---

## PR-gateway-1 — `feat/gateway-add-available-for-delivery`

**Branch**: `feat/gateway-add-available-for-delivery` (base: `main`)
**Dev**: gateway
**Scope**: agregar 1 ruta al config del gateway
**Est. LOC**: ~10 | **Files**: 1 (gateway.yaml o equivalente)

---

### T-18 — Ruta nueva en `gateway.yaml`
- **Files**: `gateway/config/*.yaml` (o equivalente)
- **TDD RED first**: N/A (config)
- **Acceptance**: ruta `GET /api/orders/available-for-delivery` → upstream `orders-service:8083`. Validar que `PUT /api/auth/profile` y `PUT /api/orders/{id}/status` ya están en el config (probablemente sí, dado que ya existían)
- **Commit**: `chore(gateway): add /api/orders/available-for-delivery route`

### T-19 — Smoke test post-deploy (opcional)
- **Files**: `gateway/tests/smoke-*.ts` (si existe el patrón)
- **Acceptance**: script que valida las 3 rutas nuevas (esta + 2 de store-flow en su PR) responden 200/401 esperados. No bloqueante
- **Commit**: `test(gateway): add smoke test for available-for-delivery`

---

## Orden de merge recomendado

1. `PR-orders-claim` primero (es el cambio de comportamiento que necesita coordinación con Flutter antes).
2. `PR-auth`, `PR-orders-status-authz`, `PR-orders-available` en paralelo.
3. `PR-gateway-1` último.

Los PRs pueden mergear en cualquier orden funcional — son archivos disjuntos. El orden sugerido es por riesgo (el claim tiene el riesgo #1 documentado).