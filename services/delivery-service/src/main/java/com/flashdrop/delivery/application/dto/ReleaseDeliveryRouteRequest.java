package com.flashdrop.delivery.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Internal request to release a claimed delivery route.
 *
 * <p>The expected delivery profile id makes the operation conditional: a
 * stale caller cannot release a route that has since been assigned to a
 * different courier.</p>
 */
public record ReleaseDeliveryRouteRequest(
        @NotNull(message = "expectedDeliveryId is required")
        @Positive(message = "expectedDeliveryId must be positive")
        Long expectedDeliveryId
) {
}
