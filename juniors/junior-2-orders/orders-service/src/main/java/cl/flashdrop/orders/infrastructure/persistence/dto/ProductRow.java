package cl.flashdrop.orders.infrastructure.persistence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductRow(
        Long id,
        @JsonProperty("restaurant_id") Long restaurantId,
        @JsonProperty("category_id") Long categoryId,
        String name,
        String description,
        BigDecimal price,
        String image,
        @JsonProperty("is_available") Boolean isAvailable,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}