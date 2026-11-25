package com.flashdrop.delivery.application.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDeliveryRouteRequest(
        @NotNull Long orderId,
        @NotBlank String pickupAddress,
        @NotBlank String deliveryAddress,
        Double distanceKm,
        Integer estimatedMinutes,
        @NotBlank
        @Pattern(
                regexp = "(?i)PENDIENTE|ASSIGNED|RETIRAR_PEDIDO|EN_CAMINO|ENTREGADO",
                message = "status must be one of PENDIENTE, ASSIGNED, RETIRAR_PEDIDO, EN_CAMINO, ENTREGADO (case-insensitive)"
        )
        String status,
        @Pattern(regexp = "(?i)NORMAL|SEGURO", message = "receptionMode must be NORMAL or SEGURO")
        String receptionMode,
        @Size(max = 120, message = "commune must be at most 120 characters")
        String commune,
        @DecimalMin(value = "-90.0", message = "pickupLatitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "pickupLatitude must be between -90 and 90")
        BigDecimal pickupLatitude,
        @DecimalMin(value = "-180.0", message = "pickupLongitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "pickupLongitude must be between -180 and 180")
        BigDecimal pickupLongitude,
        @Size(max = 64, message = "clientUserId must be at most 64 characters")
        String clientUserId,
        Long merchantId
) {

    public CreateDeliveryRouteRequest(Long orderId, String pickupAddress, String deliveryAddress,
                                      Double distanceKm, Integer estimatedMinutes, String status) {
        this(orderId, pickupAddress, deliveryAddress, distanceKm, estimatedMinutes, status,
                null, null, null, null, null, null);
    }

    public CreateDeliveryRouteRequest(Long orderId, String pickupAddress, String deliveryAddress,
                                      Double distanceKm, Integer estimatedMinutes, String status,
                                      String receptionMode) {
        this(orderId, pickupAddress, deliveryAddress, distanceKm, estimatedMinutes, status, receptionMode,
                null, null, null, null, null);
    }

    public CreateDeliveryRouteRequest(Long orderId, String pickupAddress, String deliveryAddress,
                                      Double distanceKm, Integer estimatedMinutes, String status,
                                      String receptionMode, String commune) {
        this(orderId, pickupAddress, deliveryAddress, distanceKm, estimatedMinutes, status, receptionMode,
                commune, null, null, null, null);
    }
}
