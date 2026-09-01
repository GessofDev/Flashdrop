package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.CategoryRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.UpdateProductRequest;

@Service
public class UpdateProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;
    private final CategoryRepositoryPort categoryRepositoryPort;
    private final RestaurantRepositoryPort restaurantRepositoryPort;

    public UpdateProductUseCase(
            ProductRepositoryPort productRepositoryPort,
            CategoryRepositoryPort categoryRepositoryPort,
            RestaurantRepositoryPort restaurantRepositoryPort
    ) {
        this.productRepositoryPort = productRepositoryPort;
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.restaurantRepositoryPort = restaurantRepositoryPort;
    }

    public Product execute(Long productId, UpdateProductRequest request) {
        if (request == null || !request.hasAnyChange()) {
            throw new IllegalArgumentException("Debe enviar al menos un campo para actualizar");
        }

        Product currentProduct = productRepositoryPort.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Product productToUpdate = new Product(
                currentProduct.getId(),
                request.categoryId() == null ? currentProduct.getCategoryId() : request.categoryId(),
                request.restaurantId() == null ? currentProduct.getRestaurantId() : request.restaurantId(),
                request.name() == null ? currentProduct.getName() : request.name(),
                request.description() == null ? currentProduct.getDescription() : request.description(),
                request.price() == null ? currentProduct.getPrice() : new Money(request.price()),
                request.image() == null ? currentProduct.getImage() : request.image(),
                request.available() == null ? currentProduct.isAvailable() : request.available()
        );

        validateReferences(productToUpdate);
        return productRepositoryPort.update(productToUpdate);
    }

    private void validateReferences(Product product) {
        if (!categoryRepositoryPort.existsById(product.getCategoryId())) {
            throw new ResourceNotFoundException("Category not found with id: " + product.getCategoryId());
        }

        if (!restaurantRepositoryPort.existsById(product.getRestaurantId())) {
            throw new ResourceNotFoundException("Restaurant not found with id: " + product.getRestaurantId());
        }
    }
}
