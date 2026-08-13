package com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String login,
        @NotBlank String password
) {
}
