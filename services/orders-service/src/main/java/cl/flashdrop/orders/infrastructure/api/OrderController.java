package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.command.CreateOrderCommand;
import cl.flashdrop.orders.application.dto.CreatedOrderResult;
import cl.flashdrop.orders.application.usecase.CreateOrderUseCase;
import cl.flashdrop.orders.application.usecase.GetOrderDetailUseCase;
import cl.flashdrop.orders.application.usecase.ListOrdersUseCase;
import cl.flashdrop.orders.application.usecase.UpdateOrderStatusUseCase;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.infrastructure.api.dto.request.CreateOrderRequest;
import cl.flashdrop.orders.infrastructure.api.dto.request.UpdateOrderStatusRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import cl.flashdrop.orders.infrastructure.api.dto.response.OrderDetailResponse;
import cl.flashdrop.orders.infrastructure.api.dto.response.OrderListResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controlador REST para la gestión de Pedidos.
 *
 * Mapea los endpoints expuestos en '/api/orders' respetando
 * la estructura de entrada y salida JSON del sistema original.
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

    @GetMapping
    public ApiResponse<List<OrderListResponse>> listOrders(@RequestParam(value = "user_id", required = false) UUID userId) {
        log.debug("GET /api/orders, user_id={}", userId);
        List<Order> orders = listOrdersUseCase.execute(userId);
        List<OrderListResponse> response = orders.stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(@PathVariable("id") UUID orderId) {
        log.debug("GET /api/orders/{}", orderId);
        Order order = getOrderDetailUseCase.execute(orderId);
        return ApiResponse.success(toDetailResponse(order));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatedOrderResult> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.debug("POST /api/orders");

        // Normalizar entrada soportando tanto el formato nuevo de items[]
        // como el formato heredado legacy (product_id + quantity)
        List<CreateOrderCommand.ItemRequest> cmdItems;
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            cmdItems = request.getItems().stream()
                    .map(item -> CreateOrderCommand.ItemRequest.builder()
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .build())
                    .collect(Collectors.toList());
        } else {
            cmdItems = List.of(CreateOrderCommand.ItemRequest.builder()
                    .productId(request.getProductId())
                    .quantity(request.getQuantity() != null ? request.getQuantity() : 1)
                    .build());
        }

        CreateOrderCommand command = CreateOrderCommand.builder()
                .userId(request.getUserId())
                .address(request.getAddress())
                .paymentMethod(request.getPaymentMethod())
                .distanceKm(request.getDistanceKm())
                .estimatedMinutes(request.getEstimatedMinutes())
                .items(cmdItems)
                .build();

        CreatedOrderResult result = createOrderUseCase.execute(command);
        return ApiResponse.success("Pedido creado", result);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateOrderStatus(
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        log.debug("PUT /api/orders/{}/status, status={}", orderId, request.getStatus());
        updateOrderStatusUseCase.execute(orderId, request.getStatus());
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
