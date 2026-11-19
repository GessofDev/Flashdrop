package com.flashdrop.delivery.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RouteLocationResponse(
        Long routeId,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant recordedAt
) {
}
