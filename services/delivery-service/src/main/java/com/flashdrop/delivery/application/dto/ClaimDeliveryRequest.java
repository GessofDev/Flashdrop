package com.flashdrop.delivery.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ClaimDeliveryRequest(
        @NotNull Long deliveryPersonId,
        @NotEmpty @Size(max = 3) List<Long> orderIds
) {
}