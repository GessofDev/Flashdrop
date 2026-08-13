package com.flashdrop.catalog.domain.valueobjects;

import java.math.BigDecimal;

public record Money(BigDecimal amount) {

    // Value Object: representa dinero y protege que el monto sea valido.
    // Asi evitamos repetir esta validacion en controllers o adapters.
    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("El monto es obligatorio");
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El monto no puede ser negativo");
        }
    }
}
