package com.flashdrop.catalog.application.dto;

import java.math.BigDecimal;

public record OwnerProductCommand(
        Long categoryId,
        String name,
        String description,
        BigDecimal price,
        String image,
        Boolean available
) {
}
