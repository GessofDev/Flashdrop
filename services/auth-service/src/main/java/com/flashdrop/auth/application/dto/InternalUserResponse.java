package com.flashdrop.auth.application.dto;

/**
 * Contrato interno consumido por Orders y Delivery.
 */
public record InternalUserResponse(
        Long id,
        String name,
        String lastName,
        String email,
        String phone
) {
}
