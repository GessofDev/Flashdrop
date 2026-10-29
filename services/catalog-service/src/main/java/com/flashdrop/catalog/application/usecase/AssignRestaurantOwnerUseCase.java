package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class AssignRestaurantOwnerUseCase {

    private final RestaurantRepositoryPort restaurants;

    public AssignRestaurantOwnerUseCase(RestaurantRepositoryPort restaurants) {
        this.restaurants = restaurants;
    }

    public Restaurant execute(Long restaurantId, Long userId) {
        if (restaurantId == null || restaurantId <= 0 || userId == null || userId <= 0) {
            throw new IllegalArgumentException("restaurantId y userId deben ser positivos");
        }

        Restaurant restaurant = restaurants.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found with id: " + restaurantId));
        restaurants.findByUserId(userId)
                .filter(owned -> !owned.getId().equals(restaurantId))
                .ifPresent(owned -> {
                    throw new IllegalArgumentException("El usuario ya tiene asignado otro comercio");
                });

        return restaurants.assignOwner(restaurant.getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found with id: " + restaurantId));
    }
}
