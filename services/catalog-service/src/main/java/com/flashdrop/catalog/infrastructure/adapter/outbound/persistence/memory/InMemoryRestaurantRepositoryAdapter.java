package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.memory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.Restaurant;

@Repository
@Profile("local")
public class InMemoryRestaurantRepositoryAdapter implements RestaurantRepositoryPort {

    private final List<Restaurant> restaurants = new ArrayList<>(List.of(
            new Restaurant(1L, 1L, "Flash Restaurant Demo", "Av. Providencia 1200, Santiago", LocalDateTime.now()),
            new Restaurant(2L, 2L, "Urban Burger Demo", "Los Leones 850, Santiago", LocalDateTime.now())
    ));

    @Override
    public List<Restaurant> findAll() {
        return restaurants;
    }

    @Override
    public boolean existsById(Long id) {
        return restaurants.stream()
                .anyMatch(restaurant -> restaurant.getId().equals(id));
    }

    @Override
    public Optional<Restaurant> findById(Long id) {
        return restaurants.stream()
                .filter(restaurant -> restaurant.getId().equals(id))
                .findFirst();
    }

    @Override
    public Optional<Restaurant> findByUserId(Long userId) {
        return restaurants.stream()
                .filter(restaurant -> restaurant.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public Optional<Restaurant> assignOwner(Long restaurantId, Long userId) {
        for (int index = 0; index < restaurants.size(); index++) {
            Restaurant restaurant = restaurants.get(index);
            if (restaurant.getId().equals(restaurantId)) {
                Restaurant reassigned = new Restaurant(
                        restaurant.getId(), userId, restaurant.getName(), restaurant.getAddress(),
                        restaurant.getCreatedAt());
                restaurants.set(index, reassigned);
                return Optional.of(reassigned);
            }
        }
        return Optional.empty();
    }
}
