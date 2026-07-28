package com.flashdrop.delivery.application.dto;

import java.time.Instant;

public record DeliveryPersonResponse(
        Long id,
        Long userId,
        String vehicle,
        Instant createdAt
) {
}