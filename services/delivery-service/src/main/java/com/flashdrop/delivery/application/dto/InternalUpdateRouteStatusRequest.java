package com.flashdrop.delivery.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Status update accepted by the internal route API.
 *
 * <p>The public courier endpoint keeps its narrower request DTO, so the
 * cancellation states remain reserved for the authorized internal flow.</p>
 */
public record InternalUpdateRouteStatusRequest(
        @NotBlank
        @Pattern(
                regexp = "(?i)PENDIENTE|ASSIGNED|RETIRAR_PEDIDO|EN_CAMINO|ENTREGADO|CANCELADO|RECHAZADO",
                message = "status must be a known route status (case-insensitive)"
        )
        String status
) {
}
