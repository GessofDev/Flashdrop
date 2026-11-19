package com.flashdrop.catalog.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CoordinatesTest {

    @Test
    void ofNullable_bothNull_returnsNull() {
        assertThat(Coordinates.ofNullable(null, null)).isNull();
    }

    @Test
    void ofNullable_validBounds_returnsCoordinates() {
        Coordinates coords = Coordinates.ofNullable(
                new BigDecimal("-33.448900"),
                new BigDecimal("-70.669300")
        );

        assertThat(coords).isNotNull();
        assertThat(coords.latitude()).isEqualByComparingTo("-33.448900");
        assertThat(coords.longitude()).isEqualByComparingTo("-70.669300");
    }

    @Test
    void ofNullable_boundaryValues_accepted() {
        assertThat(Coordinates.ofNullable(new BigDecimal("-90"), new BigDecimal("-180"))).isNotNull();
        assertThat(Coordinates.ofNullable(new BigDecimal("90"), new BigDecimal("180"))).isNotNull();
    }

    @Test
    void ofNullable_onlyLatitudeProvided_throwsIllegalArgument() {
        assertThatThrownBy(() -> Coordinates.ofNullable(new BigDecimal("-33.45"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofNullable_onlyLongitudeProvided_throwsIllegalArgument() {
        assertThatThrownBy(() -> Coordinates.ofNullable(null, new BigDecimal("-70.66")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofNullable_latitudeOutOfRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> Coordinates.ofNullable(new BigDecimal("90.000001"), new BigDecimal("0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Coordinates.ofNullable(new BigDecimal("-90.000001"), new BigDecimal("0")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofNullable_longitudeOutOfRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> Coordinates.ofNullable(new BigDecimal("0"), new BigDecimal("180.000001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Coordinates.ofNullable(new BigDecimal("0"), new BigDecimal("-180.000001")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
