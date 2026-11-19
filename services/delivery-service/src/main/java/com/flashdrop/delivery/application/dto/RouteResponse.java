package com.flashdrop.delivery.application.dto;

import java.math.BigDecimal;
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
        boolean deliveryPinRequired,
        String commune,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude
) {

    public RouteResponse(Long id, Long orderId, String pickupAddress, String deliveryAddress,
                         Double distanceKm, Integer estimatedMinutes, String status,
                         Instant createdAt, String code) {
        this(id, orderId, pickupAddress, deliveryAddress, distanceKm, estimatedMinutes, status,
                createdAt, code, "NORMAL", false, null, null, null);
    }

    public RouteResponse(Long id, Long orderId, String pickupAddress, String deliveryAddress,
                         Double distanceKm, Integer estimatedMinutes, String status,
                         Instant createdAt, String code, String receptionMode,
                         boolean deliveryPinRequired) {
        this(id, orderId, pickupAddress, deliveryAddress, distanceKm, estimatedMinutes, status,
                createdAt, code, receptionMode, deliveryPinRequired, null, null, null);
    }

    public RouteResponse(Long id, Long orderId, String pickupAddress, String deliveryAddress,
                         Double distanceKm, Integer estimatedMinutes, String status,
                         Instant createdAt, String code, String receptionMode,
                         boolean deliveryPinRequired, String commune) {
        this(id, orderId, pickupAddress, deliveryAddress, distanceKm, estimatedMinutes, status,
                createdAt, code, receptionMode, deliveryPinRequired, commune, null, null);
    }
}
