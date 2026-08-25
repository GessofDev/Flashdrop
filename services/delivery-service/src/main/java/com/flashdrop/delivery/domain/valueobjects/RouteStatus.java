package com.flashdrop.delivery.domain.valueobjects;

public enum RouteStatus {
    PENDIENTE("Pendiente"),
    ASSIGNED("Asignado"),
    RETIRAR_PEDIDO("Listo para retiro"),
    EN_CAMINO("En camino"),
    ENTREGADO("Entregado");

    private final String dbValue;

    RouteStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static RouteStatus fromDbValue(String value) {
        if (value == null) {
            return null;
        }
        for (RouteStatus s : values()) {
            if (s.dbValue.equalsIgnoreCase(value)) {
                return s;
            }
        }
        return RouteStatus.valueOf(value.toUpperCase().replace(' ', '_'));
    }
}