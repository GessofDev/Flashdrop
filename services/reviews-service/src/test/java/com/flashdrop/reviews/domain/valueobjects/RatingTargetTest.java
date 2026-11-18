package com.flashdrop.reviews.domain.valueobjects;

import com.flashdrop.reviews.domain.exception.InvalidTargetTypeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RatingTarget: los tres destinos posibles de una calificación (S4).
 */
@DisplayName("RatingTarget — TIENDA | REPARTIDOR | CLIENTE")
class RatingTargetTest {

    @Test
    @DisplayName("TC1: los tres tipos se parsean desde su string")
    void validValues_areParsed() {
        assertThat(RatingTarget.fromValue("TIENDA")).isEqualTo(RatingTarget.TIENDA);
        assertThat(RatingTarget.fromValue("REPARTIDOR")).isEqualTo(RatingTarget.REPARTIDOR);
        assertThat(RatingTarget.fromValue("CLIENTE")).isEqualTo(RatingTarget.CLIENTE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tienda", "Tienda", "REPARTIDOR ", "REPARTIDORES", "", "REPARTIDORX"})
    @DisplayName("TC2: un tipo desconocido o mal escrito se rechaza")
    void invalidValues_areRejected(String raw) {
        assertThatThrownBy(() -> RatingTarget.fromValue(raw))
                .isInstanceOf(InvalidTargetTypeException.class);
    }

    @Test
    @DisplayName("TC3: null se rechaza")
    void null_isRejected() {
        assertThatThrownBy(() -> RatingTarget.fromValue(null))
                .isInstanceOf(InvalidTargetTypeException.class);
    }

    @Test
    @DisplayName("TC4: el tipo tiene un valor persistible (VARCHAR(16))")
    void value_isPersistable() {
        assertThat(RatingTarget.TIENDA.name()).hasSizeLessThanOrEqualTo(16);
        assertThat(RatingTarget.REPARTIDOR.name()).hasSizeLessThanOrEqualTo(16);
        assertThat(RatingTarget.CLIENTE.name()).hasSizeLessThanOrEqualTo(16);
    }
}
