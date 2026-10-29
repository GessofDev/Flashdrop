package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignRestaurantOwnerRequest(
        @NotNull @Positive Long userId
) {
}
