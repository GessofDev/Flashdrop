package com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {
}
