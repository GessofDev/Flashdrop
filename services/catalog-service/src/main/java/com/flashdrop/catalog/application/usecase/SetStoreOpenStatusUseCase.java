package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class SetStoreOpenStatusUseCase {

    private final RestaurantRepositoryPort restaurants;

    public SetStoreOpenStatusUseCase(RestaurantRepositoryPort restaurants) {
        this.restaurants = restaurants;
    }

    public Restaurant execute(Long restaurantId, boolean acceptingOrders) {
        return restaurants.updateAcceptingOrders(restaurantId, acceptingOrders)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found with id: " + restaurantId));
    }
}
