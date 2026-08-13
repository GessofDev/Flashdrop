package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import com.flashdrop.catalog.domain.model.Category;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        String image
) {
    public static CategoryResponse fromDomain(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getImage()
        );
    }
}
