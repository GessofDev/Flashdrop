package cl.flashdrop.orders.infrastructure.persistence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record DeliveryRow(
        Long id,
        @JsonProperty("user_id") Long userId,
        String vehicle,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}