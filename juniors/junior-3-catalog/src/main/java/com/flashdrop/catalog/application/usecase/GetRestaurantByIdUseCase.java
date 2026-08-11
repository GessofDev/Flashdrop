package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;

@Service
public class GetRestaurantByIdUseCase {

    private final RestaurantRepositoryPort restaurantRepositoryPort;

    public GetRestaurantByIdUseCase(RestaurantRepositoryPort restaurantRepositoryPort) {
        this.restaurantRepositoryPort = restaurantRepositoryPort;
    }

    public Restaurant execute(Long restaurantId) {
        return restaurantRepositoryPort.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found with id: " + restaurantId
                ));
    }
}
