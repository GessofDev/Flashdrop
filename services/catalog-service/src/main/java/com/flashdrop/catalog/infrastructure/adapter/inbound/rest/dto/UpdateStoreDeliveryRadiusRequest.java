package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record UpdateStoreDeliveryRadiusRequest(
        @NotNull @DecimalMin("1") @DecimalMax("50") @Digits(integer = 2, fraction = 2)
        BigDecimal deliveryRadiusKm
) {
}
