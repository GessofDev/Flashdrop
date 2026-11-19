package com.flashdrop.delivery.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class CourierLocationTest {

    @Test
    void constructor_validInputs_createsLocation() {
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        CourierLocation loc = new CourierLocation(
                new BigDecimal("-33.448900"),
                new BigDecimal("-70.669300"),
                now
        );

        assertThat(loc.latitude()).isEqualByComparingTo("-33.448900");
        assertThat(loc.longitude()).isEqualByComparingTo("-70.669300");
        assertThat(loc.recordedAt()).isEqualTo(now);
    }

    @Test
    void constructor_nullFields_throwsIllegalArgument() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new CourierLocation(null, BigDecimal.ZERO, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourierLocation(BigDecimal.ZERO, null, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourierLocation(BigDecimal.ZERO, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_outOfRangeCoordinates_throwsIllegalArgument() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new CourierLocation(new BigDecimal("90.1"), BigDecimal.ZERO, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourierLocation(new BigDecimal("-90.1"), BigDecimal.ZERO, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourierLocation(BigDecimal.ZERO, new BigDecimal("180.1"), now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourierLocation(BigDecimal.ZERO, new BigDecimal("-180.1"), now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
