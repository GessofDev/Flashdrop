package com.flashdrop.catalog.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.flashdrop.catalog.domain.valueobjects.Money;

class ProductDimensionTest {

    private static Product product(Integer dimension) {
        return new Product(1L, 2L, 3L, "Pizza", "Grande", new Money(BigDecimal.valueOf(9000)), null, true, dimension);
    }

    @Test
    void sinDimensionElProductoQuedaSinClasificar() {
        assertNull(new Product(1L, 2L, 3L, "Pizza", "Grande", new Money(BigDecimal.valueOf(9000)), null, true)
                .getDimension());
        assertNull(product(null).getDimension());
    }

    @Test
    void aceptaLaEscalaDe1a10() {
        assertEquals(1, product(1).getDimension());
        assertEquals(10, product(10).getDimension());
    }

    @Test
    void rechazaDimensionesFueraDeLaEscala() {
        assertThrows(IllegalArgumentException.class, () -> product(0));
        assertThrows(IllegalArgumentException.class, () -> product(11));
        assertThrows(IllegalArgumentException.class, () -> product(-3));
    }
}
