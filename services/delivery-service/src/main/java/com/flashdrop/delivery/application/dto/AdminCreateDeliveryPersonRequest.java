package com.flashdrop.delivery.application.dto;

import com.flashdrop.delivery.domain.valueobjects.VehicleType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Alta de un perfil de repartidor por un administrador (F6): el usuario viene en el cuerpo. */
public record AdminCreateDeliveryPersonRequest(
        @NotNull @Positive Long userId,
        VehicleType vehicle
) {
}
