package cl.flashdrop.orders.infrastructure.persistence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DeliveryRouteRow(
        Long id,
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("pickup_address") String pickupAddress,
        @JsonProperty("delivery_address") String deliveryAddress,
        @JsonProperty("distance_km") BigDecimal distanceKm,
        @JsonProperty("estimated_minutes") Integer estimatedMinutes,
        String status,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}