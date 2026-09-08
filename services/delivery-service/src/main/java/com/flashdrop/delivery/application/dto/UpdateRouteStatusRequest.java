package com.flashdrop.delivery.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateRouteStatusRequest(
        @NotBlank
        @Pattern(
                regexp = "(?i)PENDIENTE|ASSIGNED|RETIRAR_PEDIDO|EN_CAMINO|ENTREGADO",
                message = "status must be one of PENDIENTE, ASSIGNED, RETIRAR_PEDIDO, EN_CAMINO, ENTREGADO (case-insensitive)"
        )
        String status
) {
}