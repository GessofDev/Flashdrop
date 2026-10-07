package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.math.BigDecimal;

import com.flashdrop.catalog.domain.model.Product;

public record ProductResponse(
        Long id,
        Long categoryId,
        Long restaurantId,
        String name,
        String description,
        BigDecimal price,
        String image,
        boolean available
) {
    // DTO de salida: convierte el modelo interno Product al JSON que ve el cliente.
    public static ProductResponse fromDomain(Product product) {
        return fromDomain(product, product.getImage());
    }

    public static ProductResponse fromDomain(Product product, String resolvedImage) {
        return new ProductResponse(
                product.getId(),
                product.getCategoryId(),
                product.getRestaurantId(),
                product.getName(),
                product.getDescription(),
                product.getPrice().amount(),
                resolvedImage,
                product.isAvailable()
        );
    }
}
