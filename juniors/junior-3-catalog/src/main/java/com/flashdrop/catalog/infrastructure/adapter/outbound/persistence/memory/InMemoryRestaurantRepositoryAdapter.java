package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.memory;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.Restaurant;

@Repository
@Profile("local")
public class InMemoryRestaurantRepositoryAdapter implements RestaurantRepositoryPort {

    private final List<Restaurant> restaurants = List.of(
            new Restaurant(1L, 1L, "Flash Restaurant Demo", "Av. Providencia 1200, Santiago", LocalDateTime.now()),
            new Restaurant(2L, 2L, "Urban Burger Demo", "Los Leones 850, Santiago", LocalDateTime.now())
    );

    @Override
    public List<Restaurant> findAll() {
        return restaurants;
    }
}
