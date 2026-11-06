package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.time.LocalDateTime;

import com.flashdrop.catalog.domain.model.Restaurant;

/** Vista administrativa: incluye el dueño, que el catálogo público no expone. */
public record AdminRestaurantResponse(
        Long id,
        String name,
        String address,
        Long ownerUserId,
        boolean acceptingOrders,
        boolean open
) {
    public static AdminRestaurantResponse fromDomain(Restaurant restaurant, LocalDateTime now) {
        return new AdminRestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getUserId(),
                restaurant.isAcceptingOrders(),
                restaurant.isOpenAt(now));
    }
}
