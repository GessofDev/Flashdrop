package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.supabase;

import cl.flashdrop.orders.domain.model.*;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.persistence.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SupabaseRestOrderRepositoryAdapter implements OrderRepositoryPort {

    private final RestClient supabaseRestClient;

    @Override
    public Order save(Order order) {
        OrderRow savedOrder = saveOrder(order);
        List<OrderItemRow> savedItems = saveOrderItems(savedOrder.id(), order.getItems());
        return mapToOrder(savedOrder, savedItems);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        String rawId = extractRawId(id);
        OrderRow[] rows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("id", "eq." + rawId)
                        .queryParam("select", "*")
                        .build())
                .retrieve()
                .body(OrderRow[].class);
        if (rows == null || rows.length == 0) return Optional.empty();
        OrderRow row = rows[0];
        OrderItemRow[] itemRows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/order_items")
                        .queryParam("order_id", "eq." + rawId)
                        .queryParam("select", "*")
                        .build())
                .retrieve()
                .body(OrderItemRow[].class);
        List<OrderItemRow> items = itemRows != null ? Arrays.asList(itemRows) : List.of();
        return Optional.of(mapToOrder(row, items));
    }

    @Override
    public List<Order> findAll(UUID restaurantId) {
        OrderRow[] rows;
        if (restaurantId != null) {
            rows = supabaseRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("restaurant_id", "eq." + extractRawId(restaurantId))
                            .queryParam("select", "*")
                            .queryParam("order", "id.desc")
                            .build())
                    .retrieve()
                    .body(OrderRow[].class);
        } else {
            rows = supabaseRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("select", "*")
                            .queryParam("order", "id.desc")
                            .build())
                    .retrieve()
                    .body(OrderRow[].class);
        }
        if (rows == null) return List.of();
        return Arrays.stream(rows).map(r -> mapToOrder(r, List.of())).collect(Collectors.toList());
    }

    @Override
    public void updateStatus(UUID orderId, OrderStatus status) {
        String rawId = extractRawId(orderId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.getValue());
        supabaseRestClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("id", "eq." + rawId)
                        .build())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public int claimOrders(List<UUID> orderIds, UUID deliveryId, OrderStatus status) {
        String rawDeliveryId = extractRawId(deliveryId);
        List<String> rawIds = orderIds.stream().map(this::extractRawId).collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delivery_id", rawDeliveryId);
        body.put("status", status.getValue());
        String inClause = String.join(",", rawIds);
        supabaseRestClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("id", "in.(" + inClause + ")")
                        .build())
                .body(body)
                .retrieve()
                .toBodilessEntity();
        return orderIds.size();
    }

    @Override
    public int countActiveOrdersByDelivery(UUID deliveryId) {
        String rawId = extractRawId(deliveryId);
        OrderRow[] rows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("delivery_id", "eq." + rawId)
                        .queryParam("status", "in.(En camino,Retirado)")
                        .queryParam("select", "id")
                        .queryParam("limit", "1000")
                        .build())
                .retrieve()
                .body(OrderRow[].class);
        return rows != null ? rows.length : 0;
    }

    @Override
    public List<Order> findByIdsForClaim(List<UUID> orderIds) {
        List<String> rawIds = orderIds.stream().map(this::extractRawId).collect(Collectors.toList());
        String inClause = String.join(",", rawIds);
        OrderRow[] rows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("id", "in.(" + inClause + ")")
                        .queryParam("select", "*")
                        .build())
                .retrieve()
                .body(OrderRow[].class);
        if (rows == null) return List.of();
        return Arrays.stream(rows).map(r -> mapToOrder(r, List.of())).collect(Collectors.toList());
    }

    @Override
    public void saveRoute(DeliveryRoute route) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", extractRawId(route.getOrderId()));
        body.put("pickup_address", route.getPickupAddress());
        body.put("delivery_address", route.getDeliveryAddress());
        body.put("distance_km", route.getDistanceKm());
        body.put("estimated_minutes", route.getEstimatedMinutes());
        body.put("status", route.getStatus());
        supabaseRestClient.post()
                .uri("/delivery_routes")
                .header("Prefer", "return=representation")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void updateRouteStatus(List<UUID> orderIds, String status) {
        List<String> rawIds = orderIds.stream().map(this::extractRawId).collect(Collectors.toList());
        String inClause = String.join(",", rawIds);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        supabaseRestClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/delivery_routes")
                        .queryParam("order_id", "in.(" + inClause + ")")
                        .build())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void updateRouteStatusByOrder(UUID orderId, String status) {
        String rawId = extractRawId(orderId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        supabaseRestClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/delivery_routes")
                        .queryParam("order_id", "eq." + rawId)
                        .build())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public List<Order> findAllRoutesWithOrders() {
        DeliveryRouteRow[] routeRows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/delivery_routes")
                        .queryParam("select", "*")
                        .queryParam("order", "id.desc")
                        .build())
                .retrieve()
                .body(DeliveryRouteRow[].class);
        if (routeRows == null) return List.of();
        return Arrays.stream(routeRows).map(r -> {
            OrderRow[] orderRows = supabaseRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("id", "eq." + r.orderId())
                            .queryParam("select", "*")
                            .build())
                    .retrieve()
                    .body(OrderRow[].class);
            OrderRow orderRow = (orderRows != null && orderRows.length > 0) ? orderRows[0] : null;
            if (orderRow == null) return null;
            Order order = mapToOrder(orderRow, List.of());
            order.setRoute(DeliveryRoute.builder()
                    .id(new UUID(0, r.id()))
                    .orderId(new UUID(0, r.orderId()))
                    .pickupAddress(r.pickupAddress())
                    .deliveryAddress(r.deliveryAddress())
                    .distanceKm(r.distanceKm())
                    .estimatedMinutes(r.estimatedMinutes() != null ? r.estimatedMinutes() : 0)
                    .status(r.status())
                    .build());
            return order;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    // ------ private helpers ------

    private OrderRow saveOrder(Order order) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", extractRawId(order.getClientId()));
        body.put("restaurant_id", extractRawId(order.getRestaurantId()));
        if (order.getDeliveryId() != null) body.put("delivery_id", extractRawId(order.getDeliveryId()));
        body.put("status", order.getStatus().getValue());
        body.put("address", order.getAddress());
        body.put("subtotal", order.getSubtotal());
        body.put("delivery_fee", order.getDeliveryFee());
        body.put("total", order.getTotal());
        body.put("payment_method", order.getPaymentMethod().getValue());

        OrderRow[] result = supabaseRestClient.post()
                .uri("/orders")
                .header("Prefer", "return=representation")
                .body(body)
                .retrieve()
                .body(OrderRow[].class);
        if (result == null || result.length == 0)
            throw new IllegalStateException("Error al crear orden en Supabase");
        return result[0];
    }

    private List<OrderItemRow> saveOrderItems(Long orderId, List<OrderItem> items) {
        List<Map<String, Object>> itemBodies = items.stream().map(item -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("order_id", orderId);
            b.put("product_id", extractRawId(item.getProductId()));
            b.put("quantity", item.getQuantity());
            b.put("unit_price", item.getUnitPrice());
            b.put("total", item.getLineTotal());
            return b;
        }).collect(Collectors.toList());

        OrderItemRow[] result = supabaseRestClient.post()
                .uri("/order_items")
                .header("Prefer", "return=representation")
                .body(itemBodies)
                .retrieve()
                .body(OrderItemRow[].class);
        if (result == null) return List.of();
        return Arrays.asList(result);
    }

    private String extractRawId(UUID uuid) {
        return uuid.getLeastSignificantBits() <= Integer.MAX_VALUE
                ? String.valueOf(uuid.getLeastSignificantBits())
                : uuid.toString();
    }

    private UUID toUuid(Long rawId) {
        return rawId != null ? new UUID(0, rawId) : null;
    }

    private Order mapToOrder(OrderRow row, List<OrderItemRow> itemRows) {
        List<OrderItem> items = itemRows.stream()
                .map(ir -> OrderItem.builder()
                        .id(toUuid(ir.id()))
                        .productId(toUuid(ir.productId()))
                        .quantity(ir.quantity())
                        .unitPrice(ir.unitPrice())
                        .lineTotal(ir.total())
                        .build())
                .collect(Collectors.toList());

        return Order.builder()
                .id(toUuid(row.id()))
                .clientId(toUuid(row.clientId()))
                .restaurantId(toUuid(row.restaurantId()))
                .deliveryId(toUuid(row.deliveryId()))
                .status(OrderStatus.fromValue(row.status()))
                .address(row.address())
                .subtotal(row.subtotal())
                .deliveryFee(row.deliveryFee())
                .total(row.total())
                .paymentMethod(PaymentMethod.fromValue(row.paymentMethod()))
                .createdAt(row.createdAt())
                .items(items)
                .build();
    }
}