package com.flashdrop.delivery.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRouteStatusRequest(
        @NotBlank String status
) {
}