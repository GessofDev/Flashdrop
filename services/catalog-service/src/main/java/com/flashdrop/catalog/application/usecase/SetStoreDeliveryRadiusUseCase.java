package com.flashdrop.catalog.application.usecase;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class SetStoreDeliveryRadiusUseCase {
    private final GetRestaurantByUserIdUseCase ownedRestaurant;
    private final RestaurantRepositoryPort restaurants;

    public SetStoreDeliveryRadiusUseCase(GetRestaurantByUserIdUseCase ownedRestaurant,
                                        RestaurantRepositoryPort restaurants) {
        this.ownedRestaurant = ownedRestaurant;
        this.restaurants = restaurants;
    }

    public Restaurant execute(Long userId, BigDecimal radiusKm) {
        if (radiusKm == null || radiusKm.compareTo(BigDecimal.ONE) < 0
                || radiusKm.compareTo(BigDecimal.valueOf(50)) > 0
                || radiusKm.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El radio debe estar entre 1 y 50 km, con hasta dos decimales");
        }
        Restaurant restaurant = ownedRestaurant.execute(userId);
        if (restaurant.getLatitude() == null || restaurant.getLongitude() == null) {
            throw new IllegalArgumentException("Configura primero la ubicacion del local");
        }
        return restaurants.updateDeliveryRadius(restaurant.getId(), radiusKm)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));
    }
}
