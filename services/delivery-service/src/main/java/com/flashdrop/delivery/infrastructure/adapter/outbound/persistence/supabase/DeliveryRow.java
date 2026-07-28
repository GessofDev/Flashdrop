package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.supabase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryRow(
        Long id,
        @JsonProperty("user_id") Long userId,
        String vehicle,
        @JsonProperty("created_at") Instant createdAt
) {
}