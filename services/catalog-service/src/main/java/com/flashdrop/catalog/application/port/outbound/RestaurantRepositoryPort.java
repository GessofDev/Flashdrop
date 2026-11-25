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

    List<Restaurant> findByMerchantIds(Collection<Long> merchantIds);

    Optional<Restaurant> assignMerchant(Long restaurantId, Long merchantId);

    Restaurant create(String name, String address, boolean acceptingOrders,
                      BigDecimal latitude, BigDecimal longitude);

    Restaurant createInMerchant(String name, String address, Long merchantId,
                                boolean acceptingOrders, BigDecimal latitude, BigDecimal longitude,
                                BigDecimal deliveryRadiusKm);

    default Restaurant create(String name, String address, boolean acceptingOrders) {
        return create(name, address, acceptingOrders, null, null);
    }

    Optional<Restaurant> updateAcceptingOrders(Long restaurantId, boolean acceptingOrders);

    Optional<Restaurant> replaceHours(Long restaurantId, List<OpeningHours> hours);

    Optional<Restaurant> updateLocation(Long restaurantId, BigDecimal latitude, BigDecimal longitude);

    Optional<Restaurant> updateDeliveryRadius(Long restaurantId, BigDecimal deliveryRadiusKm);
}
