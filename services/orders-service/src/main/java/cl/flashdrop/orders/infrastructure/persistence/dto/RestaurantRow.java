package cl.flashdrop.orders.infrastructure.persistence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record RestaurantRow(
        Long id,
        @JsonProperty("user_id") Long userId,
        String name,
        String address,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}