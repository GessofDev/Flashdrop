package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// DTO de entrada: representa el JSON que llega al hacer POST /catalog/products.
public record CreateProductRequest(
        @NotNull
        Long categoryId,

        @NotNull
        Long restaurantId,

        @NotBlank
        String name,

        String description,

        @NotNull
        @PositiveOrZero
        BigDecimal price,

        String image,

        Boolean available
) {
}
