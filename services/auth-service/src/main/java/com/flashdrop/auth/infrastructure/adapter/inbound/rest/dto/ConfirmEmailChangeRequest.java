package com.flashdrop.auth.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailChangeRequest(@NotBlank String token, @NotBlank String newEmail) {

    /**
     * Spring Boot desactiva globalmente {@code FAIL_ON_UNKNOWN_PROPERTIES}, asi
     * que la anotacion {@code @JsonIgnoreProperties(ignoreUnknown = false)} no
     * alcanza: los campos desconocidos hay que rechazarlos a mano.
     */
    @JsonAnySetter
    void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Campo desconocido: " + field);
    }
}
