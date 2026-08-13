package cl.flashdrop.orders.infrastructure.persistence.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record UserRow(
        Long id,
        String email,
        String rut,
        String name,
        @JsonProperty("last_name") String lastName,
        String phone,
        String photo,
        @JsonProperty("created_at") OffsetDateTime createdAt
) {}