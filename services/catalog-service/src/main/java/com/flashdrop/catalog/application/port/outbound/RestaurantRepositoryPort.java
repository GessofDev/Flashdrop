package com.flashdrop.catalog.application.port.outbound;

import java.math.BigDecimal;
import java.util.Collection;
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

    List<Restaurant> findByMerchantIds(Collection<Long> merchantIds);

    Optional<Restaurant> assignOwner(Long restaurantId, Long userId);

    Optional<Restaurant> assignMerchant(Long restaurantId, Long merchantId);

    Restaurant create(String name, String address, Long ownerUserId, boolean acceptingOrders,
                      BigDecimal latitude, BigDecimal longitude);

    Restaurant createInMerchant(String name, String address, Long ownerUserId, Long merchantId,
                                boolean acceptingOrders, BigDecimal latitude, BigDecimal longitude,
                                BigDecimal deliveryRadiusKm);

    default Restaurant create(String name, String address, Long ownerUserId, boolean acceptingOrders) {
        return create(name, address, ownerUserId, acceptingOrders, null, null);
    }

    /**
     * Elimina el vinculo del restaurante con su dueno (cuenta eliminada) y lo deja cerrado. El restaurante
     * y su catalogo se conservan. Devuelve vacio si el usuario no tenia restaurante (idempotente).
     */
    Optional<Restaurant> clearOwner(Long userId);

    Optional<Restaurant> updateAcceptingOrders(Long restaurantId, boolean acceptingOrders);

    Optional<Restaurant> replaceHours(Long restaurantId, List<OpeningHours> hours);

    Optional<Restaurant> updateLocation(Long restaurantId, BigDecimal latitude, BigDecimal longitude);

    Optional<Restaurant> updateDeliveryRadius(Long restaurantId, BigDecimal deliveryRadiusKm);
}
