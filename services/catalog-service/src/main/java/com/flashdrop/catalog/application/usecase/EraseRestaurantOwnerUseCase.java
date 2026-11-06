package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;

/**
 * Eliminacion de cuenta (F5): el dueno deja de estar vinculado a su restaurante y este queda cerrado,
 * pero el restaurante y su catalogo se conservan para poder asignarlo a otro dueno. Idempotente.
 */
@Service
public class EraseRestaurantOwnerUseCase {

    private final RestaurantRepositoryPort restaurants;

    public EraseRestaurantOwnerUseCase(RestaurantRepositoryPort restaurants) {
        this.restaurants = restaurants;
    }

    /** @return true si el usuario tenia un restaurante y se desvinculo; false si no habia nada que hacer. */
    public boolean execute(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId debe ser positivo");
        }
        return restaurants.clearOwner(userId).isPresent();
    }
}
