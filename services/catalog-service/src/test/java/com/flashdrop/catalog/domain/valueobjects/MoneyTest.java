package com.flashdrop.catalog.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void negativeAmountThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> new Money(BigDecimal.valueOf(-100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El monto no puede ser negativo");
    }
}
