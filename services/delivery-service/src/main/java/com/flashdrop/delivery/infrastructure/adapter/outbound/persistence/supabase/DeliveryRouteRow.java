package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryRouteRow(
        Long id,
        @JsonProperty("order_id") Long orderId,
        @JsonProperty("pickup_address") String pickupAddress,
        @JsonProperty("delivery_address") String deliveryAddress,
        @JsonProperty("distance_km") BigDecimal distanceKm,
        @JsonProperty("estimated_minutes") Integer estimatedMinutes,
        String status,
        @JsonProperty("created_at") Instant createdAt
) {
}