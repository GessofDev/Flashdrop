package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRestaurantRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 255) String name,
        @Size(max = 255) String address,
        Long ownerUserId
) {
}
