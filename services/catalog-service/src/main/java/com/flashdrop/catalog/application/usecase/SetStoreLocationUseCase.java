package com.flashdrop.catalog.application.usecase;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.domain.valueobjects.Coordinates;

@Service
public class SetStoreLocationUseCase {

    private final GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase;
    private final RestaurantRepositoryPort restaurants;

    public SetStoreLocationUseCase(
            GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase,
            RestaurantRepositoryPort restaurants
    ) {
        this.getRestaurantByUserIdUseCase = getRestaurantByUserIdUseCase;
        this.restaurants = restaurants;
    }

    /** Updates the store coordinates. Passing both null clears the stored pin. */
    public Restaurant execute(Long userId, BigDecimal latitude, BigDecimal longitude) {
        Coordinates.ofNullable(latitude, longitude);

        Long restaurantId = getRestaurantByUserIdUseCase.execute(userId).getId();

        return restaurants.updateLocation(restaurantId, latitude, longitude)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found with id: " + restaurantId));
    }
}
