package com.flashdrop.delivery.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

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
        String status
) {
}