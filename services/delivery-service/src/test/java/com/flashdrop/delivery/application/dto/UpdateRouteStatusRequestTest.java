package com.flashdrop.delivery.application.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateRouteStatusRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void elEndpointPublicoNoAceptaCanceladoNiRechazado() {
        assertThat(validator.validate(new UpdateRouteStatusRequest("Cancelado"))).isNotEmpty();
        assertThat(validator.validate(new UpdateRouteStatusRequest("Rechazado"))).isNotEmpty();
    }

    @Test
    void elEndpointPublicoSigueAceptandoLosEstadosExistentes() {
        assertThat(validator.validate(new UpdateRouteStatusRequest("EN_CAMINO"))).isEmpty();
    }
}
