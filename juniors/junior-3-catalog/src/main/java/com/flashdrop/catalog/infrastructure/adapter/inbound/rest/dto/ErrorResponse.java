package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

public record ErrorResponse(
        int status,
        String error,
        String message
) {
}
