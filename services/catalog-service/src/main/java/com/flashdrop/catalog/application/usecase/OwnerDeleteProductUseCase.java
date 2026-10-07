package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class OwnerDeleteProductUseCase {

    private final GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase;
    private final ProductRepositoryPort productRepositoryPort;

    public OwnerDeleteProductUseCase(
            GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase,
            ProductRepositoryPort productRepositoryPort
    ) {
        this.getRestaurantByUserIdUseCase = getRestaurantByUserIdUseCase;
        this.productRepositoryPort = productRepositoryPort;
    }

    public void execute(Long userId, Long productId) {
        Restaurant restaurant = restaurantFor(userId);
        Product currentProduct = productRepositoryPort.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + productId
                ));

        if (!restaurant.getId().equals(currentProduct.getRestaurantId())) {
            throw new OwnershipAccessDeniedException(
                    "El producto no pertenece al restaurante autenticado"
            );
        }

        productRepositoryPort.update(new Product(
                currentProduct.getId(),
                currentProduct.getCategoryId(),
                currentProduct.getRestaurantId(),
                currentProduct.getName(),
                currentProduct.getDescription(),
                currentProduct.getPrice(),
                currentProduct.getImage(),
                false
        ));
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
}
