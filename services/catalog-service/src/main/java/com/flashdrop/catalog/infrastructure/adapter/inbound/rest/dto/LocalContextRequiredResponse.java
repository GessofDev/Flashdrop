package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.util.List;

import com.flashdrop.catalog.domain.model.Restaurant;

/** Error payload returned when an operator has multiple stores and omits X-My-Store-Restaurant. */
public record LocalContextRequiredResponse(
        int status,
        String error,
        String message,
        List<AvailableRestaurant> availableRestaurants
) {
    public record AvailableRestaurant(
            Long id,
            String name,
            String address,
            Long merchantId
    ) {
        public static AvailableRestaurant fromDomain(Restaurant restaurant) {
            return new AvailableRestaurant(
                    restaurant.getId(),
                    restaurant.getName(),
                    restaurant.getAddress(),
                    restaurant.getMerchantId()
            );
        }
    }
}
