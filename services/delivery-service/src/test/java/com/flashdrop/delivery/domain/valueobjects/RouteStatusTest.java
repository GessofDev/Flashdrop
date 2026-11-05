package com.flashdrop.delivery.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteStatusTest {

    @Test
    void reconoceLosEstadosDeAnulacionEnFormatoHumano() {
        assertThat(RouteStatus.fromDbValue("Cancelado")).isEqualTo(RouteStatus.CANCELADO);
        assertThat(RouteStatus.fromDbValue("Rechazado")).isEqualTo(RouteStatus.RECHAZADO);
    }

    @Test
    void reconoceLosEstadosDeAnulacionEnFormatoDeEnum() {
        assertThat(RouteStatus.fromAnyValue("CANCELADO")).isEqualTo(RouteStatus.CANCELADO);
        assertThat(RouteStatus.fromAnyValue("RECHAZADO")).isEqualTo(RouteStatus.RECHAZADO);
    }

    @Test
    void rechazaUnEstadoDeAnulacionDesconocido() {
        assertThatThrownBy(() -> RouteStatus.fromAnyValue("INCIDENTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INCIDENTE");
    }
}
