package com.flashdrop.catalog.application.port.outbound;

import java.util.List;
import java.util.Optional;

import com.flashdrop.catalog.domain.model.OpeningHours;
import com.flashdrop.catalog.domain.model.Restaurant;

// Puerto para restaurantes: permite cambiar el origen de datos sin tocar el use case.
public interface RestaurantRepositoryPort {

    List<Restaurant> findAll();

    boolean existsById(Long id);

    Optional<Restaurant> findById(Long id);

    Optional<Restaurant> findByUserId(Long userId);

    Optional<Restaurant> assignOwner(Long restaurantId, Long userId);

    Optional<Restaurant> updateAcceptingOrders(Long restaurantId, boolean acceptingOrders);

    Optional<Restaurant> replaceHours(Long restaurantId, List<OpeningHours> hours);
}
