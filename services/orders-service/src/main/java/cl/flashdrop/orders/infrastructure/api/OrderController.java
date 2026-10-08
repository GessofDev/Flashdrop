package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.command.CreateOrderCommand;
import cl.flashdrop.orders.application.dto.CreatedOrderResult;
import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListAvailableOrdersUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.api.dto.request.CreateOrderRequest;
import cl.flashdrop.orders.infrastructure.api.dto.request.UpdateOrderStatusRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import cl.flashdrop.orders.infrastructure.api.dto.response.OrderDetailResponse;
import cl.flashdrop.orders.infrastructure.api.dto.response.OrderListResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controlador REST para la gestión de Pedidos.
 *
 * Mapea los endpoints expuestos en '/api/orders' respetando
 * la estructura de entrada y salida JSON del sistema original.
 *
 * <p><b>GAP-04 (auditoría 2026-09-04):</b> {@code /api/orders/**} ya exigía JWT
 * ({@code SecurityConfig}), pero ningún handler comprobaba que el {@code userId}
 * recibido en el body/query realmente correspondiera al usuario autenticado. Se cierra
 * para los dos puntos donde ese campo existe y es explotable:
 * <ul>
 *   <li>{@code createOrder}: el {@code userId} del pedido pasa a ser SIEMPRE el del JWT,
 *       nunca el del body (que se ignora para este propósito).</li>
 *   <li>{@code listOrders}: si se envía {@code user_id}, debe coincidir con el del JWT
 *       (403 si no); sin {@code user_id} se preserva el comportamiento existente
 *       (lista completa) — ver informe de auditoría, sección de riesgos residuales.</li>
 * </ul>
 * {@code getOrderDetail} no recibe ningún {@code userId} suplantable en su request; no se
 * le agregó un modelo de ownership nuevo que MIGRATION_PLAN.md no define.
 * {@code updateOrderStatus} sí tiene ownership desde PR-orders-status-authz (ver su Javadoc).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderDetailUseCase getOrderDetailUseCase;
    private final ListOrdersUseCase listOrdersUseCase;
    private final UpdateOrderStatusUseCase updateOrderStatusUseCase;
    private final CurrentUserResolver currentUserResolver;
    private final ListAvailableOrdersUseCase listAvailableOrdersUseCase;

    /**
     * "Mis pedidos": los pedidos del usuario autenticado según sus roles (Restaurante: los de su
     * restaurante; Cliente: los que hizo). La identidad sale siempre del JWT; el parámetro
     * {@code user_id}, si viene, debe coincidir con ella (403 si no) y no cambia el resultado.
     */
    @GetMapping
    public ApiResponse<List<OrderListResponse>> listOrders(@RequestParam(value = "user_id", required = false) Long userIdLong) {
        UUID authenticatedUserId = currentUserResolver.requireCurrentUserId();
        log.debug("GET /api/orders, user_id={}", userIdLong);
        if (userIdLong != null && !authenticatedUserId.equals(IdConverter.toUuid(userIdLong))) {
            throw new AccessDeniedException("No puedes consultar pedidos de otro usuario");
        }
        List<Order> orders = listOrdersUseCase.execute(authenticatedUserId, currentUserResolver.requireCurrentRoles());
        List<OrderListResponse> response = orders.stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(response);
    }

    /**
     * PR-orders-available (spec FR-2): pedidos de un restaurante que el repartidor puede
     * tomar (LISTO_PARA_RETIRO, sin repartidor asignado, FIFO). Solo rol Repartidor (403).
     * Wire: {@code restaurant_id} Long (lo emite catalog-service); dominio: UUID — misma
     * conversión al límite que {@link #listOrders}.
     */
    @GetMapping("/available-for-delivery")
    public ApiResponse<List<OrderListResponse>> listAvailableForDelivery(
            @RequestParam("restaurant_id") Long restaurantIdLong,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        log.debug("GET /api/orders/available-for-delivery, restaurant_id={}, limit={}", restaurantIdLong, limit);
        if (!currentUserResolver.hasRole(Role.REPARTIDOR)) {
            throw new AccessDeniedException("Solo los repartidores pueden ver pedidos disponibles");
        }
        List<Order> orders = listAvailableOrdersUseCase.execute(IdConverter.toUuid(restaurantIdLong), limit);
        return ApiResponse.success(orders.stream()
                .map(this::toListResponse)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(@PathVariable("id") UUID orderId) {
        log.debug("GET /api/orders/{}", orderId);
        Order order = getOrderDetailUseCase.execute(orderId,
                currentUserResolver.requireCurrentRoles(), currentUserResolver.requireCurrentUserId());
        return ApiResponse.success(toDetailResponse(order));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatedOrderResult> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.debug("POST /api/orders");

        // Wire: Long (lo emite catalog-service para productId). Dominio interno: UUID.
        // Conversión al límite, mismo criterio que /api/internal/orders/claim y listOrders.
        // Si el client no envió productId en ninguno de los dos formatos, queda null y el
        // use case dispara OrderDomainException ("productId obligatorio") antes de tocar catalog.
        List<CreateOrderCommand.ItemRequest> cmdItems;
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            cmdItems = request.getItems().stream()
                    .map(item -> CreateOrderCommand.ItemRequest.builder()
                            .productId(item.getProductId() != null ? IdConverter.toUuid(item.getProductId()) : null)
                            .quantity(item.getQuantity())
                            .build())
                    .collect(Collectors.toList());
        } else {
            cmdItems = List.of(CreateOrderCommand.ItemRequest.builder()
                    .productId(request.getProductId() != null ? IdConverter.toUuid(request.getProductId()) : null)
                    .quantity(request.getQuantity() != null ? request.getQuantity() : 1)
                    .build());
        }

        // GAP-04: el userId del pedido es SIEMPRE el del JWT autenticado, nunca el del
        // body — el campo request.getUserId() se ignora a propósito (ver clase Javadoc).
        UUID authenticatedUserId = currentUserResolver.requireCurrentUserId();

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(authenticatedUserId)
                .address(request.getAddress())
                .paymentMethod(request.getPaymentMethod())
                .distanceKm(request.getDistanceKm())
                .estimatedMinutes(request.getEstimatedMinutes())
                .items(cmdItems)
                .build();

        CreatedOrderResult result = createOrderUseCase.execute(command);
        return ApiResponse.success("Pedido creado", result);
    }

    /**
     * PR-orders-status-authz (spec FR-4): el use case valida rol (403), ownership del
     * restaurante para el rol Restaurante (403) y la transición desde el estado actual (409),
     * con los roles e identidad tomados del JWT — nunca del body.
     */
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateOrderStatus(
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        log.debug("PUT /api/orders/{}/status, status={}", orderId, request.getStatus());
        Set<Role> currentRoles = currentUserResolver.requireCurrentRoles();
        UUID currentUserId = currentUserResolver.requireCurrentUserId();
        updateOrderStatusUseCase.execute(orderId, request.getStatus(), currentRoles, currentUserId);
        return ApiResponse.success("Estado actualizado");
    }

    // =========================================================================
    // Mappers
    // =========================================================================

    private OrderListResponse toListResponse(Order order) {
        String clientName = order.getClientInfo() != null ? order.getClientInfo().fullName() : "Cliente";
        String clientPhone = order.getClientInfo() != null ? order.getClientInfo().getPhone() : "";
        String clientEmail = order.getClientInfo() != null ? order.getClientInfo().getEmail() : "";
        String restaurantName = order.getRestaurantInfo() != null ? order.getRestaurantInfo().getName() : "";
        UUID deliveryId = order.getDeliveryInfo() != null ? order.getDeliveryInfo().getDeliveryId() : null;

        return OrderListResponse.builder()
                .id(order.getId())
                .code(order.code())
                .status(order.getStatus().getValue())
                .address(order.getAddress())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .paymentMethod(order.getPaymentMethod().getValue())
                .clientName(clientName)
                .clientPhone(clientPhone)
                .clientEmail(clientEmail)
                .restaurantName(restaurantName)
                .deliveryId(deliveryId)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderDetailResponse toDetailResponse(Order order) {
        OrderDetailResponse.ClientDto client = null;
        if (order.getClientInfo() != null) {
            client = OrderDetailResponse.ClientDto.builder()
                    .name(order.getClientInfo().fullName())
                    .email(order.getClientInfo().getEmail())
                    .phone(order.getClientInfo().getPhone())
                    .build();
        }

        OrderDetailResponse.RestaurantDto restaurant = null;
        if (order.getRestaurantInfo() != null) {
            restaurant = OrderDetailResponse.RestaurantDto.builder()
                    .name(order.getRestaurantInfo().getName())
                    .address(order.getRestaurantInfo().getAddress())
                    .build();
        }

        OrderDetailResponse.DeliveryDto delivery = null;
        if (order.getDeliveryInfo() != null) {
            delivery = OrderDetailResponse.DeliveryDto.builder()
                    .name(order.getDeliveryInfo().fullName())
                    .phone(order.getDeliveryInfo().getPhone())
                    .vehicle(order.getDeliveryInfo().getVehicle())
                    .build();
        }

        OrderDetailResponse.RouteDto route = null;
        if (order.getRoute() != null) {
            route = OrderDetailResponse.RouteDto.builder()
                    .pickupAddress(order.getRoute().getPickupAddress())
                    .deliveryAddress(order.getRoute().getDeliveryAddress())
                    .distanceKm(order.getRoute().getDistanceKm())
                    .estimatedMinutes(order.getRoute().getEstimatedMinutes())
                    .status(order.getRoute().getStatus())
                    .build();
        }

        List<OrderDetailResponse.ItemDto> items = order.getItems().stream().map(item ->
                OrderDetailResponse.ItemDto.builder()
                        .id(item.getId())
                        .name(item.getProductName())
                        .description(item.getProductDescription())
                        .image(item.getProductImage())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .total(item.getLineTotal())
                        .build()
        ).collect(Collectors.toList());

        return OrderDetailResponse.builder()
                .id(order.getId())
                .code(order.code())
                .status(order.getStatus().getValue())
                .address(order.getAddress())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .paymentMethod(order.getPaymentMethod().getValue())
                .createdAt(order.getCreatedAt())
                .client(client)
                .restaurant(restaurant)
                .delivery(delivery)
                .route(route)
                .items(items)
                .build();
    }
}
