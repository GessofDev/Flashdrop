package com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /auth/profile}.
 *
 * <p>No trae email ni rut porque no son editables. Si el cliente los manda
 * igual, se descartan al deserializar y no llegan al caso de uso. No se
 * rechazan con 400: Spring Boot apaga FAIL_ON_UNKNOWN_PROPERTIES de forma
 * global, y {@code @JsonIgnoreProperties(ignoreUnknown = false)} no lo vuelve
 * a encender.
 *
 * <p>El formato del teléfono no se valida acá sino con {@code Phone}, en el
 * caso de uso, para que el alta y la edición apliquen la misma regla.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String lastName,
        String phone,
        @Size(max = 2048) String photo
) {
}
