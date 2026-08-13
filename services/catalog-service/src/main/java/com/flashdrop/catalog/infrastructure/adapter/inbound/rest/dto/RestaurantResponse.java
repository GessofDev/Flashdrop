package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import com.flashdrop.catalog.domain.model.Restaurant;

public record RestaurantResponse(
        Long id,
        String name,
        String address,
        String phone,
        String image
) {
    public static RestaurantResponse fromDomain(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                null,
                null
        );
    }
}
