package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.flashdrop.catalog.domain.model.RatingSummary;
import com.flashdrop.catalog.domain.model.Restaurant;

public record RestaurantResponse(
        Long id,
        String name,
        String address,
        String phone,
        String image,
        boolean open,
        boolean acceptingOrders,
        List<OpeningHoursDto> hours,
        Double ratingAverage,
        long ratingCount,
        BigDecimal latitude,
        BigDecimal longitude
) {
    /**
     * @param rating promedio cacheado de las calificaciones del local; sin
     *               calificaciones el promedio es {@code null} y el conteo 0
     */
    public static RestaurantResponse fromDomain(Restaurant restaurant, LocalDateTime now, RatingSummary rating) {
        RatingSummary summary = rating == null ? RatingSummary.none() : rating;
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                null,
                null,
                restaurant.isOpenAt(now),
                restaurant.isAcceptingOrders(),
                restaurant.getHours().stream().map(OpeningHoursDto::fromDomain).toList(),
                summary.average(),
                summary.count(),
                restaurant.getLatitude(),
                restaurant.getLongitude()
        );
    }
}
