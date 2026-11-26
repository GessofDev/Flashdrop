package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for a DUEÑO creating a new store inside their merchant group. */
public record CreateMyStoreRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 255) String name,
        @Size(max = 255) String address,
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        BigDecimal latitude,
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        BigDecimal longitude,
        @DecimalMin(value = "0.5", message = "Delivery radius must be between 0.5 and 50 km")
        @DecimalMax(value = "50.0", message = "Delivery radius must be between 0.5 and 50 km")
        @Digits(integer = 2, fraction = 2, message = "Delivery radius supports up to 2 decimal places")
        BigDecimal deliveryRadiusKm
) {
    public CreateMyStoreRequest(String name, String address) {
        this(name, address, null, null, null);
    }
}
