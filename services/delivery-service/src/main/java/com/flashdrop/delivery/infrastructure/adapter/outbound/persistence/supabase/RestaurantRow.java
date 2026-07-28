package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RestaurantRow(
        Long id,
        @JsonProperty("user_id") Long userId,
        String name,
        String address
) {
}