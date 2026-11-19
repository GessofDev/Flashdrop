package com.flashdrop.catalog.application.usecase;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.OwnerAlreadyAssignedException;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.domain.valueobjects.Coordinates;

/**
 * Alta administrativa de un restaurante (F6). Nace cerrado: el dueño lo abre cuando configura su
 * horario. El dueño es opcional; si viene, no puede tener ya otro restaurante.
 */
@Service
public class CreateRestaurantUseCase {

    private final RestaurantRepositoryPort restaurants;

    public CreateRestaurantUseCase(RestaurantRepositoryPort restaurants) {
        this.restaurants = restaurants;
    }

    public Restaurant execute(String name, String address, Long ownerUserId) {
        return execute(name, address, ownerUserId, null, null);
    }

    public Restaurant execute(String name, String address, Long ownerUserId,
                              BigDecimal latitude, BigDecimal longitude) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del restaurante es obligatorio");
        }
        Coordinates.ofNullable(latitude, longitude);
        if (ownerUserId != null) {
            if (ownerUserId <= 0) {
                throw new IllegalArgumentException("ownerUserId debe ser positivo");
            }
            if (restaurants.findByUserId(ownerUserId).isPresent()) {
                throw new OwnerAlreadyAssignedException("El usuario ya tiene asignado otro comercio");
            }
        }
        String normalizedAddress = address == null || address.isBlank() ? null : address.trim();
        return restaurants.create(name.trim(), normalizedAddress, ownerUserId, false, latitude, longitude);
    }
}
