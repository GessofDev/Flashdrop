package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderRow(
        Long id,
        @JsonProperty("client_id") Long clientId,
        @JsonProperty("restaurant_id") Long restaurantId,
        @JsonProperty("delivery_id") Long deliveryId,
        String status,
        String address,
        String code
) {
}