package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.CategoryRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Product;

@Service
public class CreateProductUseCase {

    // Spring inyecta aqui el adapter que implemente ProductRepositoryPort segun el perfil activo.
    private final ProductRepositoryPort productRepositoryPort;
    private final CategoryRepositoryPort categoryRepositoryPort;
    private final RestaurantRepositoryPort restaurantRepositoryPort;

    public CreateProductUseCase(
            ProductRepositoryPort productRepositoryPort,
            CategoryRepositoryPort categoryRepositoryPort,
            RestaurantRepositoryPort restaurantRepositoryPort
    ) {
        this.productRepositoryPort = productRepositoryPort;
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.restaurantRepositoryPort = restaurantRepositoryPort;
    }

    public Product execute(Product product) {
        validateReferences(product);
        return productRepositoryPort.save(product);
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
