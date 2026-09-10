package com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto;

/**
 * Formato de error acordado para los contratos internos entre servicios.
 */
public record ErrorResponse(int status, String error, String message) {
}
