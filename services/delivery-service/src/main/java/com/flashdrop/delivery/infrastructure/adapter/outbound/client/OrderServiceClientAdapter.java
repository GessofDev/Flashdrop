package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase.OrderRow;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase.RestaurantRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrderServiceClientAdapter implements OrderServicePort {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceClientAdapter.class);

    private final RestClient supabase;

    public OrderServiceClientAdapter(RestClient supabaseRestClient) {
        this.supabase = supabaseRestClient;
    }

    @Override
    public List<OrderInfo> getOrdersByIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        String inList = orderIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        OrderRow[] rows = supabase.get()
                .uri("/orders?id=in.({ids})&select=id,client_id,restaurant_id,delivery_id,status,address", inList)
                .retrieve()
                .body(OrderRow[].class);
        if (rows == null || rows.length == 0) {
            return List.of();
        }

        List<Long> restaurantIds = Arrays.stream(rows)
                .map(OrderRow::restaurantId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, RestaurantRow> restaurantsById = loadRestaurants(restaurantIds);

        return Arrays.stream(rows)
                .map(row -> toOrderInfo(row, restaurantsById))
                .toList();
    }

    @Override
    public boolean areOrdersFromSameRestaurant(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return true;
        }
        List<OrderInfo> orders = getOrdersByIds(orderIds);
        long distinctRestaurants = orders.stream()
                .map(OrderInfo::restaurantId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        return distinctRestaurants == 1;
    }

    private Map<Long, RestaurantRow> loadRestaurants(List<Long> restaurantIds) {
        if (restaurantIds.isEmpty()) {
            return Map.of();
        }
        String inList = restaurantIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            RestaurantRow[] rows = supabase.get()
                    .uri("/restaurant?id=in.({ids})&select=id,user_id,name,address", inList)
                    .retrieve()
                    .body(RestaurantRow[].class);
            if (rows == null) {
                return Map.of();
            }
            return Arrays.stream(rows).collect(Collectors.toMap(RestaurantRow::id, r -> r));
        } catch (Exception ex) {
            log.warn("Could not load restaurant addresses: {}", ex.getMessage());
            return Map.of();
        }
    }

    private OrderInfo toOrderInfo(OrderRow row, Map<Long, RestaurantRow> restaurantsById) {
        String pickup = "Restaurant " + row.restaurantId();
        RestaurantRow restaurant = restaurantsById.get(row.restaurantId());
        if (restaurant != null && restaurant.address() != null && !restaurant.address().isBlank()) {
            pickup = restaurant.name() != null
                    ? restaurant.name() + ", " + restaurant.address()
                    : restaurant.address();
        }
        String delivery = row.address() != null ? row.address() : "";
        return new OrderInfo(row.id(), row.restaurantId(), pickup, delivery);
    }
}