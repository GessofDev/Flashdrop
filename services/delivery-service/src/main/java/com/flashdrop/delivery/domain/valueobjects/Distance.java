package com.flashdrop.delivery.domain.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

public record Distance(BigDecimal value) {

    public Distance {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Distance cannot be null or negative");
        }
    }

    public static Distance of(BigDecimal value) {
        return new Distance(value);
    }

    public static Distance zero() {
        return new Distance(BigDecimal.ZERO);
    }

    public BigDecimal getValue() {
        return value;
    }
}
