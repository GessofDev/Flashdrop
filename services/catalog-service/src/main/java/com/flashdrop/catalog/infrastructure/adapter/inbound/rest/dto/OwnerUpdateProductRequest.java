package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.math.BigDecimal;

import com.flashdrop.catalog.application.dto.OwnerProductCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OwnerUpdateProductRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        @NotNull @Positive BigDecimal price,
        @Pattern(
                regexp = "^products/\\d{4}/(0[1-9]|1[0-2])/[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|webp)$",
                message = "debe ser un object key de producto valido"
        )
        String image,
        @NotNull Boolean available
) {
    public OwnerProductCommand toCommand() {
        return new OwnerProductCommand(categoryId, name, description, price, image, available);
    }
}
