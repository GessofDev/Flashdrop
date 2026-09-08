package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class GetRestaurantByUserIdUseCase {

    private final RestaurantRepositoryPort restaurantRepositoryPort;

    public GetRestaurantByUserIdUseCase(RestaurantRepositoryPort restaurantRepositoryPort) {
        this.restaurantRepositoryPort = restaurantRepositoryPort;
    }

    public Restaurant execute(Long userId) {
        return restaurantRepositoryPort.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found for userId: " + userId
                ));
    }
}
