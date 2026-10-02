package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.dto.OwnerProductCommand;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.domain.valueobjects.Money;

@Service
public class OwnerCreateProductUseCase {

    private final GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase;
    private final CreateProductUseCase createProductUseCase;

    public OwnerCreateProductUseCase(
            GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase,
            CreateProductUseCase createProductUseCase
    ) {
        this.getRestaurantByUserIdUseCase = getRestaurantByUserIdUseCase;
        this.createProductUseCase = createProductUseCase;
    }

    public Product execute(Long userId, OwnerProductCommand command) {
        Restaurant restaurant = restaurantFor(userId);
        Product product = new Product(
                null,
                command.categoryId(),
                restaurant.getId(),
                command.name(),
                command.description(),
                new Money(command.price()),
                command.image(),
                command.available() == null || command.available()
        );

        return createProductUseCase.execute(product);
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
