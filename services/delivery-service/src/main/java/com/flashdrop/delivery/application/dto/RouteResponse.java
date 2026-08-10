package com.flashdrop.delivery.application.dto;

import java.time.Instant;

public record RouteResponse(
        Long id,
        Long orderId,
        String pickupAddress,
        String deliveryAddress,
        Double distanceKm,
        Integer estimatedMinutes,
        String status,
        Instant createdAt,
        String code
) {
}