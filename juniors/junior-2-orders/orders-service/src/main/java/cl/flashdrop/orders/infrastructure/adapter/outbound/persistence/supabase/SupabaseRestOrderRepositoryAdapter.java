package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.supabase;

import cl.flashdrop.orders.domain.model.*;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.persistence.dto.OrderItemRow;
import cl.flashdrop.orders.infrastructure.persistence.dto.OrderRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adaptador REST a Supabase (PostgREST) para las tablas PROPIAS de Orders:
 * {@code orders} y {@code order_items}.
 *
 * <p>No accede a tablas externas (products, restaurant, users, delivery, delivery_routes);
 * esas consultas se resuelven vía los adaptadores HTTP ({@code Catalog}, {@code Auth}, {@code Delivery}).</p>
 */
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
        long rawId = IdConverter.toLong(id);
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
        String select = "*";
        OrderRow[] rows;
        if (restaurantId != null) {
            rows = supabaseRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("restaurant_id", "eq." + IdConverter.toLong(restaurantId))
                            .queryParam("select", select)
                            .queryParam("order", "id.desc")
                            .build())
                    .retrieve()
                    .body(OrderRow[].class);
        } else {
            rows = supabaseRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("select", select)
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
        long rawId = IdConverter.toLong(orderId);
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
        long rawDeliveryId = IdConverter.toLong(deliveryId);
        List<Long> rawIds = orderIds.stream().map(IdConverter::toLong).collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delivery_id", rawDeliveryId);
        body.put("status", status.getValue());
        String inClause = rawIds.stream().map(Object::toString).collect(Collectors.joining(","));
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
        long rawId = IdConverter.toLong(deliveryId);
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
        List<Long> rawIds = orderIds.stream().map(IdConverter::toLong).collect(Collectors.toList());
        String inClause = rawIds.stream().map(Object::toString).collect(Collectors.joining(","));
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
    public List<Order> findByIds(List<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return List.of();
        List<Long> rawIds = orderIds.stream().map(IdConverter::toLong).collect(Collectors.toList());
        String inClause = rawIds.stream().map(Object::toString).collect(Collectors.joining(","));
        OrderRow[] rows = supabaseRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders")
                        .queryParam("id", "in.(" + inClause + ")")
                        .queryParam("select", "*")
                        .build())
                .retrieve()
                .body(OrderRow[].class);
        if (rows == null) return List.of();
        List<UUID> idList = orderIds.stream().map(IdConverter::toLong).map(IdConverter::toUuid).collect(Collectors.toList());
        Set<UUID> idSet = new HashSet<>(idList);
        return Arrays.stream(rows)
                .map(r -> mapToOrder(r, List.of()))
                .filter(o -> idSet.contains(o.getId()))
                .collect(Collectors.toList());
    }

    // ------ private helpers ------

    private OrderRow saveOrder(Order order) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", IdConverter.toLong(order.getClientId()));
        body.put("restaurant_id", IdConverter.toLong(order.getRestaurantId()));
        if (order.getDeliveryId() != null) body.put("delivery_id", IdConverter.toLong(order.getDeliveryId()));
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
            b.put("product_id", IdConverter.toLong(item.getProductId()));
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

    private Order mapToOrder(OrderRow row, List<OrderItemRow> itemRows) {
        List<OrderItem> items = itemRows.stream()
                .map(ir -> OrderItem.builder()
                        .id(IdConverter.toUuid(ir.id()))
                        .productId(IdConverter.toUuid(ir.productId()))
                        .quantity(ir.quantity())
                        .unitPrice(ir.unitPrice())
                        .lineTotal(ir.total())
                        .build())
                .collect(Collectors.toList());

        return Order.builder()
                .id(IdConverter.toUuid(row.id()))
                .clientId(IdConverter.toUuid(row.clientId()))
                .restaurantId(IdConverter.toUuid(row.restaurantId()))
                .deliveryId(row.deliveryId() != null ? IdConverter.toUuid(row.deliveryId()) : null)
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
