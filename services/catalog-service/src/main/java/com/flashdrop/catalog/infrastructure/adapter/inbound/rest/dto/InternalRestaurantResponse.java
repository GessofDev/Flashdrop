package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import com.flashdrop.catalog.domain.model.Restaurant;

public record InternalRestaurantResponse(
        Long id,
        Long userId,
        String name,
        String address
) {
    public static InternalRestaurantResponse fromDomain(Restaurant restaurant) {
        return new InternalRestaurantResponse(
                restaurant.getId(),
                restaurant.getUserId(),
                restaurant.getName(),
                restaurant.getAddress()
        );
    }
}
