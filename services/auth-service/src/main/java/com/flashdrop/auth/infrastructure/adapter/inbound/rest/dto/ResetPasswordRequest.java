package com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
}
