package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.dto.OwnerProductCommand;
import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.UpdateProductRequest;

@Service
public class OwnerUpdateProductUseCase {

    private final GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase;
    private final ProductRepositoryPort productRepositoryPort;
    private final UpdateProductUseCase updateProductUseCase;

    public OwnerUpdateProductUseCase(
            GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase,
            ProductRepositoryPort productRepositoryPort,
            UpdateProductUseCase updateProductUseCase
    ) {
        this.getRestaurantByUserIdUseCase = getRestaurantByUserIdUseCase;
        this.productRepositoryPort = productRepositoryPort;
        this.updateProductUseCase = updateProductUseCase;
    }

    public Product execute(Long userId, Long productId, OwnerProductCommand command) {
        Restaurant restaurant = restaurantFor(userId);
        Product currentProduct = productRepositoryPort.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + productId
                ));
        assertOwnership(currentProduct, restaurant);

        return updateProductUseCase.execute(
                productId,
                new UpdateProductRequest(
                        command.categoryId(),
                        restaurant.getId(),
                        command.name(),
                        command.description(),
                        command.price(),
                        command.image(),
                        command.available()
                )
        );
    }

    private Restaurant restaurantFor(Long userId) {
        try {
            return getRestaurantByUserIdUseCase.execute(userId);
        } catch (ResourceNotFoundException exception) {
            throw new OwnershipAccessDeniedException(
                    "No existe un restaurante asociado al usuario autenticado"
            );
        }
    }

    private void assertOwnership(Product product, Restaurant restaurant) {
        if (!restaurant.getId().equals(product.getRestaurantId())) {
            throw new OwnershipAccessDeniedException(
                    "El producto no pertenece al restaurante autenticado"
            );
        }
    }
}
