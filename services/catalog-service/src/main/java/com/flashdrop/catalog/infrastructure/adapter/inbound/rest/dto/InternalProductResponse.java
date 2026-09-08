package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashdrop.catalog.domain.model.Product;

public record InternalProductResponse(
        Long id,
        Long restaurantId,
        String name,
        String description,
        BigDecimal price,
        String image,
        @JsonProperty("isAvailable") boolean available
) {
    public static InternalProductResponse fromDomain(Product product) {
        return new InternalProductResponse(
                product.getId(),
                product.getRestaurantId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().amount(),
                product.getImage(),
                product.isAvailable()
        );
    }
}
