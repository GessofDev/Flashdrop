# Tasks: `profile-and-delivery`

## PR Chain

**Strategy**: parallel-by-owner (cada dev dueño de su servicio mergea su PR en cualquier orden; `PR-gateway-1` espera al final).

```
main
  ├── feat/auth-update-profile                       (PR-auth)
  ├── feat/orders-status-authz                        (PR-orders-status-authz)
  ├── feat/orders-available-for-delivery              (PR-orders-available)
  ├── feat/delivery-claim-no-status-mutation          (PR-delivery)
  └── feat/gateway-add-available-for-delivery         (PR-gateway-1, último)
```

---

## PR-auth — `feat/auth-update-profile`

**Branch**: `feat/auth-update-profile` (base: `main`)
**Dev**: auth
**Scope**: `PUT /auth/profile` con use case + DTO + tests
**Est. LOC**: ~120 | **Files**: ~5

---

### T-1 — DTO `UpdateProfileRequest`
- **Files**: `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/dto/UpdateProfileRequest.java`
- **TDD RED first**: sí — definir el DTO con validaciones Jakarta Validation (`@NotBlank`, `@Size`, `@Pattern` donde aplique)
- **Acceptance**: record con `name`, `lastName`, `phone`, `photo`. Validaciones activas. Rechaza campos extra (`@JsonIgnoreProperties(ignoreUnknown = false)`)
- **Commit**: `feat(auth): add UpdateProfileRequest DTO with strict validation`

### T-2 — Use case `UpdateUserProfileUseCase`
- **Files**:
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/port/inbound/UpdateUserProfileUseCase.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/usecase/UpdateUserProfileService.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/dto/UpdateUserProfileCommand.java`
- **TDD RED first**: sí — `UpdateUserProfileServiceTest` antes de implementar
- **Acceptance**: el use case recibe `(userId, command)`, busca el usuario, aplica cambios, valida colisión de `phone` (409 si hay otro user con ese phone), guarda. Devuelve `UserProfile` actualizado
- **Commit**: `feat(auth): add UpdateUserProfileUseCase with phone collision check`

### T-3 — `PUT /auth/profile` en `AuthController`
- **Files**: `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/AuthController.java`
- **TDD RED first**: sí — `AuthControllerTest` con MockMvc verifica 200, 400, 401, 409
- **Acceptance**: handler que toma el `userId` del JWT (vía `validateToken.validate(bearer)`), llama al use case, devuelve `UserProfile` envuelto en `ApiResponse`. Sin parámetros de `userId` en el body
- **Commit**: `feat(auth): add PUT /auth/profile endpoint`

### T-4 — Registrar use case en configuración
- **Files**: `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/config/UseCaseConfiguration.java`
- **TDD RED first**: N/A (wiring)
- **Acceptance**: `@Bean` para `UpdateUserProfileUseCase`. La app arranca sin errores
- **Commit**: `chore(auth): register UpdateUserProfileUseCase in config`

### T-5 — Tests de integration `AuthControllerIT`
- **Files**: `services/auth-service/src/test/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/AuthControllerIT.java`
- **TDD RED first**: el archivo puede crearse con el test RED junto a T-3
- **Acceptance**: tests con Testcontainers + JWT firmado. Casos: perfil válido (200), phone colisiona (409), JWT inválido (401), body con email (rechazado por strict schema → 400)
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
- **Commit**: `feat(orders): add RoleTransitionPolicy with delivery store-owner admin matrix`

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
- **Acceptance**: matriz 2D (delivery, store_owner, admin) × (transición válida, transición inválida por estado, transición prohibida por rol). Mínimo 8 casos. Cubre los códigos 200, 403, 409
- **Commit**: `test(orders): add role-based status transition integration tests`

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
- **Acceptance**: casos: happy path con 5 pedidos (devuelve 5), happy path con 3 pedidos (devuelve 3), filtro por estado (no devuelve NUEVO_PEDIDO ni PREPARANDO), límite (no devuelve más de N), 403 si rol no delivery, 400 si falta restaurant_id, 400 si limit > 50
- **Commit**: `test(orders): add available-for-delivery integration tests`

---

## PR-delivery — `feat/delivery-claim-no-status-mutation`

**Branch**: `feat/delivery-claim-no-status-mutation` (base: `main`)
**Dev**: delivery
**Scope**: `claim` deja de mutar `Order.status`
**Est. LOC**: ~60 | **Files**: ~3

---

### T-15 — Ajustar `Order.assignDelivery`
- **Files**: `services/delivery-service/src/main/java/com/flashdrop/delivery/domain/model/Order.java` (si vive ahí) **o** `services/orders-service/src/main/java/cl/flashdrop/orders/domain/model/Order.java` (si vive en orders). **Decisión durante la implementación**: el `Order` actualmente vive en orders-service, pero `Order.assignDelivery` parece estar duplicado o referenciado desde delivery-service. Verificar antes.
- **TDD RED first**: sí — test del modelo
- **Acceptance**: el método `assignDelivery(deliveryId)` persiste `deliveryId` **sin modificar `status`**. Si el método se llamaba antes para transicionar a `EN_CAMINO`, ese comportamiento se elimina
- **Commit**: `feat(delivery): persist deliveryId without mutating Order status in assignDelivery`

### T-16 — Ajustar `ClaimDeliveryOrdersUseCaseImpl`
- **Files**: `services/delivery-service/src/main/java/com/flashdrop/delivery/application/usecase/ClaimDeliveryOrdersUseCaseImpl.java`
- **TDD RED first**: sí — actualizar `ClaimDeliveryOrdersUseCaseImplTest` para verificar que `Order.status` no se modifica
- **Acceptance**: eliminar la línea (o bloque) que mutaba el estado. El use case solo persiste `deliveryId` en `delivery_routes` y (si el flag está activo) llama a `internalOrdersClient.claimOrders`
- **Commit**: `feat(delivery): remove Order status mutation from ClaimDeliveryOrdersUseCase`

### T-17 — Test explícito del nuevo comportamiento
- **Files**: `services/delivery-service/src/test/java/com/flashdrop/delivery/application/usecase/ClaimDeliveryOrdersUseCaseImplTest.java`
- **Acceptance**: tests que mockean el repo y verifican que después de `execute(userId, request)`, el `Order` retornado (o el que se podría consultar) tiene `status = LISTO_PARA_RETIRO` sin cambios, pero `deliveryId` poblado
- **Commit**: `test(delivery): verify ClaimDeliveryOrdersUseCase does not mutate Order status`

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

1. `PR-delivery` primero (es el cambio de comportamiento que necesita coordinación con Flutter antes).
2. `PR-auth`, `PR-orders-status-authz`, `PR-orders-available` en paralelo.
3. `PR-gateway-1` último.

Los PRs pueden mergear en cualquier orden funcional — son archivos disjuntos. El orden sugerido es por riesgo (delivery-service tiene el riesgo #1 documentado).