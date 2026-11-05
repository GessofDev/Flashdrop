package com.flashdrop.catalog.application.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.OpeningHours;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class SetStoreHoursUseCase {

    private final GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase;
    private final RestaurantRepositoryPort restaurants;

    public SetStoreHoursUseCase(
            GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase,
            RestaurantRepositoryPort restaurants
    ) {
        this.getRestaurantByUserIdUseCase = getRestaurantByUserIdUseCase;
        this.restaurants = restaurants;
    }

    /** Reemplaza la semana completa. Una lista vacía deja al local sin horario definido. */
    public Restaurant execute(Long userId, List<OpeningHours> hours) {
        long days = hours.stream().map(OpeningHours::day).distinct().count();
        if (days != hours.size()) {
            throw new IllegalArgumentException("El horario admite un solo tramo por dia");
        }

        Long restaurantId = getRestaurantByUserIdUseCase.execute(userId).getId();

        return restaurants.replaceHours(restaurantId, hours)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found with id: " + restaurantId));
    }
}
