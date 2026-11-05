package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.flashdrop.catalog.domain.model.Restaurant;

public record RestaurantResponse(
        Long id,
        String name,
        String address,
        String phone,
        String image,
        boolean open,
        boolean acceptingOrders,
        List<OpeningHoursDto> hours
) {
    public static RestaurantResponse fromDomain(Restaurant restaurant, LocalDateTime now) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                null,
                null,
                restaurant.isOpenAt(now),
                restaurant.isAcceptingOrders(),
                restaurant.getHours().stream().map(OpeningHoursDto::fromDomain).toList()
        );
    }
}
