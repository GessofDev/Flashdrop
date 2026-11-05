package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.memory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.function.UnaryOperator;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.OpeningHours;
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
        return replace(restaurantId, restaurant -> new Restaurant(
                restaurant.getId(), userId, restaurant.getName(), restaurant.getAddress(),
                restaurant.getCreatedAt(), restaurant.isAcceptingOrders(), restaurant.getHours()));
    }

    @Override
    public Optional<Restaurant> updateAcceptingOrders(Long restaurantId, boolean acceptingOrders) {
        return replace(restaurantId, restaurant -> new Restaurant(
                restaurant.getId(), restaurant.getUserId(), restaurant.getName(), restaurant.getAddress(),
                restaurant.getCreatedAt(), acceptingOrders, restaurant.getHours()));
    }

    @Override
    public Optional<Restaurant> replaceHours(Long restaurantId, List<OpeningHours> hours) {
        return replace(restaurantId, restaurant -> new Restaurant(
                restaurant.getId(), restaurant.getUserId(), restaurant.getName(), restaurant.getAddress(),
                restaurant.getCreatedAt(), restaurant.isAcceptingOrders(), hours));
    }

    private Optional<Restaurant> replace(Long restaurantId, UnaryOperator<Restaurant> change) {
        for (int index = 0; index < restaurants.size(); index++) {
            Restaurant restaurant = restaurants.get(index);
            if (restaurant.getId().equals(restaurantId)) {
                Restaurant updated = change.apply(restaurant);
                restaurants.set(index, updated);
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }
}
