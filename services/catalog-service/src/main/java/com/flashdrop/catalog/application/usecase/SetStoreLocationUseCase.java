package com.flashdrop.catalog.application.usecase;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.domain.valueobjects.Coordinates;

@Service
public class SetStoreLocationUseCase {

    private final RestaurantRepositoryPort restaurants;

    public SetStoreLocationUseCase(RestaurantRepositoryPort restaurants) {
        this.restaurants = restaurants;
    }

    /** Updates the store coordinates. Passing both null clears the stored pin. */
    public Restaurant execute(Long restaurantId, BigDecimal latitude, BigDecimal longitude) {
        Coordinates.ofNullable(latitude, longitude);

        return restaurants.updateLocation(restaurantId, latitude, longitude)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found with id: " + restaurantId));
    }
}
