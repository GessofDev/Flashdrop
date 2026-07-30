package com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String email,
        @NotBlank String password,
        String rut,
        String name,
        String lastName,
        String phone
) {
}
