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

    Restaurant create(String name, String address, Long ownerUserId, boolean acceptingOrders);

    /**
     * Elimina el vinculo del restaurante con su dueno (cuenta eliminada) y lo deja cerrado. El restaurante
     * y su catalogo se conservan. Devuelve vacio si el usuario no tenia restaurante (idempotente).
     */
    Optional<Restaurant> clearOwner(Long userId);

    Optional<Restaurant> updateAcceptingOrders(Long restaurantId, boolean acceptingOrders);

    Optional<Restaurant> replaceHours(Long restaurantId, List<OpeningHours> hours);
}
