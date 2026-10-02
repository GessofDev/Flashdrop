package cl.flashdrop.orders.domain.model;

import lombok.Getter;

import java.time.Duration;

/**
 * Rango del resumen de ventas de un restaurante (spec store-flow FR-1). El período se
 * calcula hacia atrás desde "ahora": {@code day} = últimas 24h, {@code week} = últimos 7
 * días, {@code month} = últimos 30 días.
 */
@Getter
public enum SalesRange {

    DAY("day", Duration.ofHours(24)),
    WEEK("week", Duration.ofDays(7)),
    MONTH("month", Duration.ofDays(30));

    private final String value;
    private final Duration length;

    SalesRange(String value, Duration length) {
        this.value = value;
        this.length = length;
    }

    public static SalesRange fromValue(String value) {
        for (SalesRange range : values()) {
            if (range.value.equals(value)) {
                return range;
            }
        }
        throw new IllegalArgumentException("Rango no reconocido: " + value);
    }
}
