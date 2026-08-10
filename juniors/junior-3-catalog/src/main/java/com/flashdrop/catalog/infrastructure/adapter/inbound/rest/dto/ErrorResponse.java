package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String error,
        String message,
        OffsetDateTime timestamp
) {
}
