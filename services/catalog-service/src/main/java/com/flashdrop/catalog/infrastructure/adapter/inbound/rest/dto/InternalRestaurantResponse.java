package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.flashdrop.catalog.domain.model.Restaurant;

public record InternalRestaurantResponse(
        Long id,
        Long userId,
        String name,
        String address,
        boolean open,
        BigDecimal latitude,
        BigDecimal longitude
) {
    public static InternalRestaurantResponse fromDomain(Restaurant restaurant, LocalDateTime now) {
        return new InternalRestaurantResponse(
                restaurant.getId(),
                restaurant.getUserId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.isOpenAt(now),
                restaurant.getLatitude(),
                restaurant.getLongitude()
        );
    }
}
