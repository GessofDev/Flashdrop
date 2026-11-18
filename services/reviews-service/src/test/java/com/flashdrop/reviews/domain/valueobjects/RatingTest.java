package com.flashdrop.reviews.domain.valueobjects;

import com.flashdrop.reviews.domain.exception.InvalidRatingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rating (S5): escala entera de 1 a 5 estrellas, obligatoria.
 */
@DisplayName("Rating — escala de 1 a 5 estrellas")
class RatingTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("TC1: los valores 1..5 son válidos")
    void validRange_isAccepted(int value) {
        assertThat(Rating.of(value).value()).isEqualTo(value);
    }

    @Test
    @DisplayName("TC2: el límite inferior (1) y el superior (5) son válidos")
    void boundaries_areAccepted() {
        assertThat(Rating.of(Rating.MIN).value()).isEqualTo(1);
        assertThat(Rating.of(Rating.MAX).value()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 6, 100})
    @DisplayName("TC3: un rating fuera de rango se rechaza")
    void outOfRange_isRejected(int value) {
        assertThatThrownBy(() -> Rating.of(value))
                .isInstanceOf(InvalidRatingException.class)
                .hasMessageContaining(String.valueOf(value));
    }

    @Test
    @DisplayName("TC4: el mensaje de error dice el rango permitido")
    void outOfRange_explainsAllowedRange() {
        assertThatThrownBy(() -> Rating.of(0))
                .isInstanceOf(InvalidRatingException.class)
                .hasMessageContaining("1")
                .hasMessageContaining("5");
    }
}
