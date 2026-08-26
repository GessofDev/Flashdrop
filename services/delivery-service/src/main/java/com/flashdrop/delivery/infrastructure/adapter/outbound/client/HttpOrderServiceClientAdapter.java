package com.flashdrop.delivery.infrastructure.adapter.outbound.client;

import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP client adapter that calls orders-service over REST.
 *
 * <p>Endpoints called (contract from MIGRATION_PLAN.md §8.3, §8.2):
 * <ul>
 *   <li>GET /api/internal/orders?ids={id1,id2,...} — fetch order details by IDs.
 *       Response: [{ id, clientId, restaurantId, deliveryId, status, address }] (all ids: long).</li>
 *   <li>GET /api/internal/restaurants/{restaurantId} — fetch one restaurant by ID.
 *       Looped N times for batch (the contract does not define a batch endpoint).</li>
 * </ul>
 *
 * <p>Authentication: {@code X-Internal-Api-Key} header on every request.
 *
 * <p>Graceful degradation: any HTTP failure (timeout, 5xx, connection refused, 404
 * because orders-service has not yet implemented the endpoint) is caught, logged at
 * WARN with the current trace ID, and returns an empty list. The caller
 * ({@link com.flashdrop.delivery.application.usecase.ClaimDeliveryOrdersUseCaseImpl})
 * will then throw {@code IllegalArgumentException("Some orders were not found")},
 * making the behaviour suitable for testing before orders-service is ready.
 */
@Component
public class HttpOrderServiceClientAdapter implements OrderServicePort {

    private static final Logger log = LoggerFactory.getLogger(HttpOrderServiceClientAdapter.class);

    private final RestClient restClient;

    public HttpOrderServiceClientAdapter(RestClient ordersServiceRestClient) {
        this.restClient = ordersServiceRestClient;
    }

    // Package-private constructor for testing with injected base URL
    HttpOrderServiceClientAdapter(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
    }

    @Override
    public List<OrderInfo> getOrdersByIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        String idsParam = orderIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        try {
            OrderResponse[] responses = restClient.get()
                    .uri("/api/internal/orders?ids={ids}", idsParam)
                    .retrieve()
                    .body(OrderResponse[].class);

            if (responses == null || responses.length == 0) {
                return List.of();
            }

            // Collect unique restaurant IDs to fetch one-by-one (no batch endpoint in MIGRATION_PLAN §8.2)
            List<Long> restaurantIds = java.util.Arrays.stream(responses)
                    .map(r -> r.restaurantId())
                    .filter(id -> id != null)
                    .distinct()
                    .toList();

            Map<Long, RestaurantInfo> restaurantsById = loadRestaurants(restaurantIds);

            return java.util.Arrays.stream(responses)
                    .map(row -> toOrderInfo(row, restaurantsById))
                    .toList();

        } catch (RestClientException ex) {
            log.warn("[{}] orders-service /api/internal/orders call failed (endpoint not ready or unreachable), returning empty list: {}",
                    currentTraceId(), ex.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean areOrdersFromSameRestaurant(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return true;
        }
        List<OrderInfo> orders = getOrdersByIds(orderIds);
        long distinctRestaurants = orders.stream()
                .map(OrderInfo::restaurantId)
                .filter(id -> id != null)
                .distinct()
                .count();
        return distinctRestaurants == 1;
    }

    private Map<Long, RestaurantInfo> loadRestaurants(List<Long> restaurantIds) {
        Map<Long, RestaurantInfo> result = new HashMap<>();
        if (restaurantIds.isEmpty()) {
            return result;
        }

        for (Long restaurantId : restaurantIds) {
            try {
                RestaurantResponse response = restClient.get()
                        .uri("/api/internal/restaurants/{id}", restaurantId)
                        .retrieve()
                        .body(RestaurantResponse.class);
                if (response != null && response.id() != null) {
                    result.put(response.id(),
                            new RestaurantInfo(response.id(), response.name(), response.address()));
                }
            } catch (RestClientException ex) {
                // Per-restaurant failure: log + skip, continue with the others.
                // If catalog-service has not yet implemented the endpoint, all calls fail
                // and the map ends up empty — pickup falls back to "Restaurant {id}" in toOrderInfo.
                log.warn("[{}] orders-service /api/internal/restaurants/{} call failed: {}",
                        currentTraceId(), restaurantId, ex.getMessage());
            }
        }
        return result;
    }

    private OrderInfo toOrderInfo(OrderResponse row, Map<Long, RestaurantInfo> restaurantsById) {
        String pickup = "Restaurant " + row.restaurantId();
        RestaurantInfo restaurant = restaurantsById.get(row.restaurantId());
        if (restaurant != null && restaurant.address() != null && !restaurant.address().isBlank()) {
            pickup = restaurant.name() != null
                    ? restaurant.name() + ", " + restaurant.address()
                    : restaurant.address();
        }
        String delivery = row.address() != null ? row.address() : "";
        return new OrderInfo(row.id(), row.restaurantId(), pickup, delivery, row.code());
    }

    /**
     * Returns the current trace ID from MDC, or "n/a" if not set.
     * The MDC key is "traceId" (as set by CorrelationIdFilter in shared-observability).
     */
    private String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "n/a";
    }

    // -------------------------------------------------------------------------
    // Internal DTOs mirroring orders-service API response (per MIGRATION_PLAN.md §8.3, §8.2)
    // -------------------------------------------------------------------------

    /**
     * Response DTO for {@code GET /api/internal/orders?ids=...}.
     * Per MIGRATION_PLAN.md §8.3: id/clientId/restaurantId/deliveryId are all long.
     */
    record OrderResponse(
            Long id,
            Long clientId,
            Long restaurantId,
            Long deliveryId,
            String status,
            String address,
            String code
    ) {}

    /**
     * Response DTO for {@code GET /api/internal/restaurants/{restaurantId}}.
     * Per MIGRATION_PLAN.md §8.2.
     */
    record RestaurantResponse(
            Long id,
            Long userId,
            String name,
            String address
    ) {}

    record RestaurantInfo(Long id, String name, String address) {}
}
