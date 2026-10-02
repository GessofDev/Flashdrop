package com.flashdrop.catalog.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.ProductRepositoryPort;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class OwnerListProductsUseCase {

    private final GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase;
    private final ProductRepositoryPort productRepositoryPort;

    public OwnerListProductsUseCase(
            GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase,
            ProductRepositoryPort productRepositoryPort
    ) {
        this.getRestaurantByUserIdUseCase = getRestaurantByUserIdUseCase;
        this.productRepositoryPort = productRepositoryPort;
    }

    public List<Product> execute(Long userId) {
        Restaurant restaurant = restaurantFor(userId);
        return productRepositoryPort.findByRestaurantId(restaurant.getId());
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
