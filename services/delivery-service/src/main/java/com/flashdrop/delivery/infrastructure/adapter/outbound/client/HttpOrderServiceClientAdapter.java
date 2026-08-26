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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HTTP client adapter that calls orders-service over REST.
 *
 * <p>Endpoints called:
 * <ul>
 *   <li>GET /api/orders?ids={id1,id2,...} — fetch order details by IDs</li>
 *   <li>GET /api/internal/restaurants?ids={id1,id2,...} — fetch restaurant names/addresses</li>
 * </ul>
 *
 * <p>Authentication: {@code X-Internal-Api-Key} header on every request.
 *
 * <p>Graceful degradation: if the HTTP call fails (timeout, 5xx, connection refused),
 * returns an empty list and logs a WARN with the current trace ID. The caller
 * ({@link com.flashdrop.delivery.application.usecase.ClaimDeliveryOrdersUseCaseImpl})
 * will receive an empty list and throw an appropriate error, making the failure
 * behaviour suitable for automated testing.
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
                    .uri("/api/orders?ids={ids}", idsParam)
                    .retrieve()
                    .body(OrderResponse[].class);

            if (responses == null || responses.length == 0) {
                return List.of();
            }

            // Collect unique restaurant IDs to fetch in batch
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
            log.warn("[{}] orders-service HTTP call failed, returning empty list: {}",
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
        if (restaurantIds.isEmpty()) {
            return Map.of();
        }

        String idsParam = restaurantIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        try {
            RestaurantResponse[] responses = restClient.get()
                    .uri("/api/internal/restaurants?ids={ids}", idsParam)
                    .retrieve()
                    .body(RestaurantResponse[].class);

            if (responses == null) {
                return Map.of();
            }

            return java.util.Arrays.stream(responses)
                    .collect(Collectors.toMap(
                            r -> r.id(),
                            r -> new RestaurantInfo(r.id(), r.name(), r.address())
                    ));

        } catch (RestClientException ex) {
            log.warn("[{}] orders-service restaurant fetch failed: {}",
                    currentTraceId(), ex.getMessage());
            return Map.of();
        }
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
        // orders-service uses UUID; delivery-service stores Long — take last 8 bytes
        Long orderId = uuidToLong(row.id());
        return new OrderInfo(orderId, row.restaurantId(), pickup, delivery, row.code());
    }

    /** Converts a UUID to a Long by taking the last 8 bytes (variant + most-significant bits). */
    private Long uuidToLong(UUID uuid) {
        if (uuid == null) return null;
        return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
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
    // Internal DTOs mirroring orders-service API response
    // -------------------------------------------------------------------------

    /**
     * Response DTO for the /api/orders endpoint.
     * The actual API returns UUID as string, but we parse the last 8 bytes as Long.
     */
    record OrderResponse(
            UUID id,
            Long restaurantId,
            String status,
            String address,
            String code
    ) {}

    record RestaurantResponse(
            Long id,
            String name,
            String address
    ) {}

    record RestaurantInfo(Long id, String name, String address) {}
}
