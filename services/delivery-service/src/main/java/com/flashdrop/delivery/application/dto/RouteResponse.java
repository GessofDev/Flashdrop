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
        String code,
        String receptionMode,
        boolean deliveryPinRequired
) {

    public RouteResponse(Long id, Long orderId, String pickupAddress, String deliveryAddress,
                         Double distanceKm, Integer estimatedMinutes, String status,
                         Instant createdAt, String code) {
        this(id, orderId, pickupAddress, deliveryAddress, distanceKm, estimatedMinutes, status,
                createdAt, code, "NORMAL", false);
    }
}