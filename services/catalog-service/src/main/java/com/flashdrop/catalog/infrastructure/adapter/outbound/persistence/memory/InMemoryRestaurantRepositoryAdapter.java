package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.memory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
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
            new Restaurant(1L, null, 1L, "Flash Restaurant Demo", "Av. Providencia 1200, Santiago",
                    LocalDateTime.now(), true, List.of(), null, null, null),
            new Restaurant(2L, null, 2L, "Urban Burger Demo", "Los Leones 850, Santiago",
                    LocalDateTime.now(), true, List.of(), null, null, null)
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
    public List<Restaurant> findByMerchantIds(Collection<Long> merchantIds) {
        if (merchantIds == null || merchantIds.isEmpty()) {
            return List.of();
        }
        return restaurants.stream()
                .filter(restaurant -> restaurant.getMerchantId() != null
                        && merchantIds.contains(restaurant.getMerchantId()))
                .toList();
    }

    @Override
    public Optional<Restaurant> assignMerchant(Long restaurantId, Long merchantId) {
        return replace(restaurantId, restaurant -> new Restaurant(
                restaurant.getId(), restaurant.getUserId(), merchantId, restaurant.getName(), restaurant.getAddress(),
                restaurant.getCreatedAt(), restaurant.isAcceptingOrders(), restaurant.getHours(),
                restaurant.getLatitude(), restaurant.getLongitude(), restaurant.getDeliveryRadiusKm()));
    }

    @Override
    public Restaurant create(String name, String address, boolean acceptingOrders,
                             BigDecimal latitude, BigDecimal longitude) {
        return createInMerchant(name, address, null, acceptingOrders, latitude, longitude, null);
    }

    @Override
    public Restaurant createInMerchant(String name, String address, Long merchantId,
                                       boolean acceptingOrders, BigDecimal latitude, BigDecimal longitude,
                                       BigDecimal deliveryRadiusKm) {
        long nextId = restaurants.stream().mapToLong(Restaurant::getId).max().orElse(0L) + 1;
        Restaurant created = new Restaurant(nextId, null, merchantId, name, address, LocalDateTime.now(),
                acceptingOrders, List.of(), latitude, longitude, deliveryRadiusKm);
        restaurants.add(created);
        return created;
    }

    @Override
    public Optional<Restaurant> updateAcceptingOrders(Long restaurantId, boolean acceptingOrders) {
        return replace(restaurantId, restaurant -> new Restaurant(
                restaurant.getId(), restaurant.getUserId(), restaurant.getMerchantId(),
                restaurant.getName(), restaurant.getAddress(),
                restaurant.getCreatedAt(), acceptingOrders, restaurant.getHours(),
                restaurant.getLatitude(), restaurant.getLongitude(), restaurant.getDeliveryRadiusKm()));
    }

    @Override
    public Optional<Restaurant> replaceHours(Long restaurantId, List<OpeningHours> hours) {
        return replace(restaurantId, restaurant -> new Restaurant(
                restaurant.getId(), restaurant.getUserId(), restaurant.getMerchantId(),
                restaurant.getName(), restaurant.getAddress(),
                restaurant.getCreatedAt(), restaurant.isAcceptingOrders(), hours,
                restaurant.getLatitude(), restaurant.getLongitude(), restaurant.getDeliveryRadiusKm()));
    }

    @Override
    public Optional<Restaurant> updateLocation(Long restaurantId, BigDecimal latitude, BigDecimal longitude) {
        return replace(restaurantId, restaurant -> new Restaurant(
                restaurant.getId(), restaurant.getUserId(), restaurant.getMerchantId(),
                restaurant.getName(), restaurant.getAddress(),
                restaurant.getCreatedAt(), restaurant.isAcceptingOrders(), restaurant.getHours(),
                latitude, longitude, restaurant.getDeliveryRadiusKm()));
    }

    @Override
    public Optional<Restaurant> updateDeliveryRadius(Long restaurantId, BigDecimal deliveryRadiusKm) {
        return replace(restaurantId, restaurant -> new Restaurant(
                restaurant.getId(), restaurant.getUserId(), restaurant.getMerchantId(),
                restaurant.getName(), restaurant.getAddress(),
                restaurant.getCreatedAt(), restaurant.isAcceptingOrders(), restaurant.getHours(),
                restaurant.getLatitude(), restaurant.getLongitude(), deliveryRadiusKm));
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
