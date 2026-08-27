package com.flashdrop.delivery.domain.valueobjects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum RouteStatus {
    PENDIENTE("Pendiente"),
    ASSIGNED("Asignado"),
    RETIRAR_PEDIDO("Listo para retiro"),
    EN_CAMINO("En camino"),
    ENTREGADO("Entregado");

    private static final Logger log = LoggerFactory.getLogger(RouteStatus.class);

    private final String dbValue;

    RouteStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    /**
     * Maps a raw DB value to the canonical enum, tolerating legacy/unknown values
     * by returning {@code null} instead of throwing. Callers MUST treat a {@code null}
     * result as a row to inspect or skip (e.g., a route whose status was set by an
     * older deployment or via an unenforced DTO).
     */
    public static RouteStatus fromDbValue(String value) {
        if (value == null) {
            return null;
        }
        for (RouteStatus s : values()) {
            if (s.dbValue.equalsIgnoreCase(value) || s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        log.warn("Unknown RouteStatus dbValue='{}' — returning null instead of throwing", value);
        return null;
    }

    /**
     * Strict mapping for the WRITE path (use case / controller input). Accepts both
     * the enum name ({@code ENTREGADO}) and the dbValue ({@code Entregado}) in any
     * casing. Throws {@link IllegalArgumentException} with a descriptive message when
     * the value matches neither — this is the contract that {@code @Pattern} on the
     * DTO complements but cannot fully enforce (the use case is the last line of
     * defense for the domain rule).
     */
    public static RouteStatus fromAnyValue(String value) {
        RouteStatus mapped = fromDbValue(value);
        if (mapped == null) {
            throw new IllegalArgumentException("Invalid status: " + value);
        }
        return mapped;
    }
}