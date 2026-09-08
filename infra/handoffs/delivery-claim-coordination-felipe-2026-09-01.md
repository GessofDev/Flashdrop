# Hand-off — orders-service /api/internal/orders/claim

**Date**: 2026-09-01
**From**: delivery-service owner (Sebastián)
**To**: orders-service owner (Felipe)
**Status**: PR-A (delivery-side) shipped. PR-B (delivery-side) is ready and feature-flagged OFF. This hand-off unblocks the final flag flip.

## TL;DR

Felipe, necesitamos que expongas `POST /api/internal/orders/claim` en orders-service, detrás de `X-Internal-Api-Key`. El código ya existe — es un caso de uso + DTO + port — pero **falta el controller** que lo conecta al HTTP. Una vez que esté arriba, del lado delivery flipeamos `DELIVERY_CLAIM_DELEGATE_TO_ORDERS=true` y queda cerrado el flag de IMPORTANTE sobre rutas huérfanas.

Cinco cambios pequeños. Tiempo estimado: 1-2 horas.

## Contexto

QA flagueó que `delivery-service`:
1. (CRÍTICO) No tiene auth en sus endpoints públicos → ya cerrado en PR-A con JWT validation.
2. (IMPORTANTE) `POST /api/delivery/claim` crea rutas pero nunca actualiza `orders.delivery_id` / `orders.status`. Las rutas quedan huérfanas.

Este hand-off cierra el (2). El plan canónico está en `C:\Users\ASUS TUF F15 i5\.claude\plans\golden-inventing-church.md` (decisiones D1-D10). El source of truth para contratos cross-service sigue siendo `references/migration-plan/MIGRATION_PLAN.md` §3.4 (canónica lookup `/api/internal/delivery-persons?userId=...`) y §9.3 (wire = Long).

## Wire shape (delivery → orders)

```
POST http://orders-service:8083/api/internal/orders/claim
Headers:
  X-Internal-Api-Key: <shared-secret>
  Content-Type: application/json
Body:
  { "userId": <long>, "orderIds": [<long>, ...] }
```

**Importante**: el `userId` que llega es el `user_id` del repartidor (subject del JWT, parseado como `Long`). NO es `delivery.id`. Orders resuelve `userId` → `delivery.id` usando su propio `DeliveryPort.findDeliveryIdByUserId(Long)`, que internamente pega contra `GET /api/internal/delivery-persons?userId=<long>` (canónica del plan §3.4).

Single source of truth para el mapeo userId↔deliveryId vive en delivery. No hace falta IdConverter, ni conversión UUID, ni otro mecanismo.

## Cambios necesarios

### 1. NEW controller — `DeliveryClaimController.java`

Path: `services/orders-service/src/main/java/cl/flashdrop/orders/infrastructure/api/DeliveryClaimController.java`

```java
package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ClaimDeliveryOrdersUseCase;
import cl.flashdrop.orders.infrastructure.api.dto.request.ClaimDeliveryRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/internal/orders")
@RequiredArgsConstructor
public class DeliveryClaimController {

    private final ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    @PostMapping("/claim")
    public ApiResponse<Void> claim(@Valid @RequestBody ClaimDeliveryRequest request) {
        log.debug("POST /api/internal/orders/claim userId={}, orderIds={}",
                request.getUserId(), request.resolvedOrderIds());
        claimDeliveryOrdersUseCase.execute(request.getUserId(), request.resolvedOrderIds());
        return ApiResponse.success("Pedido reclamado");
    }
}
```

~25 LOC. Reusa use case + DTO + exception handler existentes.

### 2. MODIFY DTO — `ClaimDeliveryRequest.java`

Path: `services/orders-service/src/main/java/cl/flashdrop/orders/infrastructure/api/dto/request/ClaimDeliveryRequest.java`

Rename `deliveryPersonId` → `userId`, flip 3 fields UUID → Long, mantener `@JsonAlias` para compatibilidad con clientes legacy + el contrato viejo de `openapi.yaml`.

```java
package cl.flashdrop.orders.infrastructure.api.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO de entrada para que un repartidor tome pedidos para delivery
 * (POST /api/internal/orders/claim).
 *
 * Formato principal (delivery → orders, post-PR-B):
 *   { "userId": <long>, "orderIds": [<long>, ...] }
 *
 * Formato legacy compatible (Node.js original + openapi.yaml viejo):
 *   { "user_id": <long>, "order_ids": [<long>, ...] }
 *   { "deliveryPersonId": <long>, "orderIds": [<long>, ...] }
 *
 * Nota: el valor de `userId`/`user_id`/`deliveryPersonId` es el user_id
 * del repartidor (subject del JWT), NO delivery.id. Orders resuelve
 * userId → delivery.id vía DeliveryPort.findDeliveryIdByUserId.
 */
@Getter
@Setter
@NoArgsConstructor
public class ClaimDeliveryRequest {

    @NotNull(message = "El userId es obligatorio")
    @JsonAlias({"user_id", "deliveryPersonId"})
    private Long userId;

    @JsonAlias({"order_id"})
    private Long orderId;

    @JsonAlias({"order_ids"})
    private List<Long> orderIds;

    public List<Long> resolvedOrderIds() {
        if (orderIds != null && !orderIds.isEmpty()) {
            return orderIds;
        }
        if (orderId != null) {
            return List.of(orderId);
        }
        return List.of();
    }
}
```

Cambios netos:
- `private UUID deliveryPersonId` → `private Long userId` (1 rename + 1 type flip)
- `private UUID orderId` → `private Long orderId` (1 type flip)
- `private List<UUID> orderIds` → `private List<Long> orderIds` (1 type flip)
- `resolvedOrderIds()`: cambia firma `List<UUID>` → `List<Long>` (sin cambio de lógica)
- `getUserId()` getter: ahora retorna `Long` (sin cambio de lógica)
- Comentarios actualizados

### 3. MODIFY use case — `ClaimDeliveryOrdersUseCase.java`

Path: `services/orders-service/src/main/java/cl/flashdrop/orders/application/usecase/ClaimDeliveryOrdersUseCase.java`

Flip firma `execute(UUID, List<UUID>)` → `execute(Long, List<Long>)`. **NO tocar la línea `deliveryPort.findDeliveryIdByUserId(userId)`** — sigue siendo la canónica de lookup (migration plan §3.4).

```java
@Transactional
public void execute(Long userId, List<Long> orderIds) {
    // 1. Validar cantidad — sin cambios
    List<Long> uniqueOrderIds = orderIds.stream().distinct().collect(Collectors.toList());
    if (uniqueOrderIds.isEmpty() || uniqueOrderIds.size() > maxClaimPerRoute) {
        throw new OrderDomainException(
                "Debes seleccionar entre 1 y " + maxClaimPerRoute + " pedidos para tomar la ruta");
    }

    // 2. Verificar perfil de repartidor — TIPOS flips. La línea se queda.
    Long deliveryId = deliveryPort.findDeliveryIdByUserId(userId)
            .orElseThrow(() -> new OrderDomainException("El usuario no tiene perfil de repartidor"));

    // 3-6. Validaciones — sin cambios (sólo tipos de variables intermedias)
    int activeOrders = orderRepository.countActiveOrdersByDelivery(deliveryId);
    if (activeOrders > 0) {
        throw new OrderDomainException(
                "Ya tienes pedidos en ruta. Termina tu ruta antes de tomar mas pedidos");
    }

    List<Order> orders = orderRepository.findByIdsForClaim(uniqueOrderIds);
    if (orders.size() != uniqueOrderIds.size()) {
        throw new OrderDomainException("Uno o mas pedidos ya no estan disponibles");
    }

    boolean hasClosed = orders.stream().anyMatch(o -> o.getStatus().isClosed());
    if (hasClosed) {
        throw new OrderDomainException("Uno o mas pedidos ya fueron tomados por otro repartidor");
    }

    Set<Long> restaurants = orders.stream()
            .map(Order::getRestaurantId)
            .collect(Collectors.toSet());
    if (restaurants.size() > 1) {
        throw new OrderDomainException("Solo puedes agrupar pedidos del mismo restaurante");
    }

    // 7. Asignar — tipos flips
    int updated = orderRepository.claimOrders(uniqueOrderIds, deliveryId, OrderStatus.EN_CAMINO);
    if (updated != uniqueOrderIds.size()) {
        throw new OrderDomainException(
                "Alguien tomo uno de estos pedidos antes que tu. Actualiza la lista");
    }

    // 8. Sincronizar rutas — sin cambios
    orderRepository.updateRouteStatus(uniqueOrderIds, OrderStatus.EN_CAMINO.getValue());

    log.info("Repartidor {} tomó {} pedidos: {}", deliveryId, uniqueOrderIds.size(), uniqueOrderIds);
}
```

Quitar el `import java.util.UUID;` si ya no se usa en el archivo.

### 4. MODIFY port — `OrderRepositoryPort.java`

Path: `services/orders-service/src/main/java/cl/flashdrop/orders/domain/port/OrderRepositoryPort.java`

Flip firma `claimOrders`:

```java
int claimOrders(List<Long> orderIds, Long deliveryId, OrderStatus status);
```

Y actualizar TODAS las firmas del archivo que usan UUID → Long. Revisé el archivo hoy: las firmas son `save(Order)`, `findById(UUID id)` → `findById(Long id)`, `findAll(UUID restaurantId)` → `findAll(Long restaurantId)`, `updateStatus(UUID orderId, OrderStatus)` → `updateStatus(Long, OrderStatus)`, `countActiveOrdersByDelivery(UUID)` → `countActiveOrdersByDelivery(Long)`, `findByIdsForClaim(List<UUID>)` → `findByIdsForClaim(List<Long>)`, `updateRouteStatus(List<UUID>, String)` → `updateRouteStatus(List<Long>, String)`, `updateRouteStatusByOrder(UUID)` → `updateRouteStatusByOrder(Long)`.

**OJO**: este cambio es BLOB — afecta `OrderRepositoryPort` Y todas las implementaciones (`SupabaseRestOrderRepositoryAdapter`, eventualmente `JpaOrderRepositoryAdapter`). Recomendación: hacerlo en pasos atómicos (1 port + 1 impl + 1 test) para no romper el build. Si tu `SupabaseRestOrderRepositoryAdapter` sigue activo, los `extractRawId(UUID)` se transforman en `String.valueOf(Long)` — verificar cada callsite.

### 5. MODIFY port — `DeliveryPort.java` (opcional)

Path: `services/orders-service/src/main/java/cl/flashdrop/orders/domain/port/DeliveryPort.java`

Si decidís hacer el flip consistente, los métodos quedan:

```java
Optional<Long> findClientIdByUserId(Long userId);
Optional<ClientInfo> findClientById(Long clientId);
Optional<Long> findDeliveryIdByUserId(Long userId);
Optional<DeliveryInfo> findDeliveryById(Long deliveryId);
```

**Recomendado**: hacerlo en este PR porque sino queda inconsistente. Pero si te bloquea el `ClientInfo`/`DeliveryInfo` que también reciben UUID, se puede postergar y dejar el cambio en un PR de cleanup. Tu llamada.

## NO TOCAR

- `services/orders-service/src/main/java/cl/flashdrop/orders/config/SecurityConfig.java` — `anyRequest().permitAll()` ya cubre `/api/internal/**`. NO requiere cambios.
- `services/orders-service/src/main/java/cl/flashdrop/orders/infrastructure/exception/GlobalExceptionHandler.java` — ya mapea `OrderDomainException` a error estructurado.
- `services/orders-service/src/main/resources/db/migration/V1__init.sql` — orders DB es `bigint`, no UUID. NO migrar.
- `services/orders-service/src/main/java/cl/flashdrop/orders/infrastructure/api/OrderController.java` — `/api/orders/*` queda intacto. El nuevo controller vive aparte en `/api/internal/orders/claim`.

## Verificación (después del merge de Felipe)

### Unit test del nuevo controller

```bash
cd services/orders-service && ./mvnw test -Dtest=DeliveryClaimControllerTest
```

(Si no lo agregás, MockMvc funciona igual con un test minimal.)

### Wire-level manual smoke (del lado delivery)

```bash
# Flag ON (después del merge de los dos PRs)
DELIVERY_CLAIM_DELEGATE_TO_ORDERS=true \
  curl -X POST http://orders-service:8083/api/internal/orders/claim \
  -H "X-Internal-Api-Key: $INTERNAL_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"userId": 5, "orderIds": [101, 102]}'

# Esperado: 200 ApiResponse.success("Pedido reclamado")
# Verificación en DB:
psql orders_db -c "SELECT id, delivery_id, status FROM orders WHERE id IN (101, 102);"
# Debe mostrar delivery_id != NULL, status = 'En camino'
```

### Happy path end-to-end (courier real)

1. Courier se loguea en auth-service → JWT con `sub=5`.
2. Courier pega `POST /delivery/claim` con `{"orderIds": [101, 102]}` y `Authorization: Bearer <jwt>`.
3. Delivery valida JWT, extrae userId=5, persiste las rutas en `delivery.delivery_routes`.
4. Delivery (con flag ON) llama a orders con `{userId: 5, orderIds: [101, 102]}`.
5. Orders resuelve 5 → delivery.id (Long PK), valida, hace `UPDATE orders SET delivery_id=<delivery.id>, status='En camino' WHERE id IN (101, 102)`.
6. Orders responde 200 → delivery responde 200 al courier.

## Out of scope (no en este hand-off)

- Documentación `openapi.yaml` (tu llamada — podés repoint `/api/delivery/claim` a `/api/internal/orders/claim` o agregar entrada nueva).
- Cleanup del patrón "fake UUID" en `SupabaseRestOrderRepositoryAdapter.toUuid(Long)` / `extractRawId(UUID)` — funciona, limpieza separada.
- Cleanup del `JwtValidationFilter` remote-call anti-pattern — refactor de auth aparte.
- Reconciliación de rutas huérfanas pre-existentes — job separado.

## Referencias

- Plan canónico: `C:\Users\ASUS TUF F15 i5\.claude\plans\golden-inventing-church.md` (decisiones D1-D10, QA feedback log 14 rows).
- Source of truth contratos cross-service: `references/migration-plan/MIGRATION_PLAN.md` §3.4, §9.3.
- PR-A delivery-side (security + IDOR): merged, 96/100 tests pass (4 ITs Docker-bound).
- PR-B delivery-side: ready, feature-flagged `DELIVERY_CLAIM_DELEGATE_TO_ORDERS=false` por default.
- AGENTS.md: convenciones (Conventional Commits, service boundaries, Java style).

## Preguntas / bloqueos

Cualquier duda con el wire shape, los tipos, o el flujo de validación → avisame. Una vez mergeado, flipeamos el flag y cerramos el ticket de IMPORTANTE.