package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class SetStoreOpenStatusUseCase {

    private final GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase;
    private final RestaurantRepositoryPort restaurants;

    public SetStoreOpenStatusUseCase(
            GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase,
            RestaurantRepositoryPort restaurants
    ) {
        this.getRestaurantByUserIdUseCase = getRestaurantByUserIdUseCase;
        this.restaurants = restaurants;
    }

    public Restaurant execute(Long userId, boolean acceptingOrders) {
        // El local siempre se deriva del usuario autenticado, nunca de la solicitud.
        Long restaurantId = getRestaurantByUserIdUseCase.execute(userId).getId();

        return restaurants.updateAcceptingOrders(restaurantId, acceptingOrders)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found with id: " + restaurantId));
    }
}
