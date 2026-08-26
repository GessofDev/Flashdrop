package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.PositiveOrZero;

// DTO de entrada para PATCH interno: todos los campos son opcionales.
public record UpdateProductRequest(
        Long categoryId,
        Long restaurantId,
        String name,
        String description,
        @PositiveOrZero BigDecimal price,
        String image,
        Boolean available
) {
    public boolean hasAnyChange() {
        return categoryId != null
                || restaurantId != null
                || name != null
                || description != null
                || price != null
                || image != null
                || available != null;
    }
}
