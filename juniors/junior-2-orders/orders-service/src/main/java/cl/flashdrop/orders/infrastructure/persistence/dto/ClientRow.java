package cl.flashdrop.orders.infrastructure.persistence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record ClientRow(
        Long id,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("end_date") OffsetDateTime endDate
) {}