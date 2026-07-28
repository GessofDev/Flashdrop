package com.flashdrop.delivery.domain.valueobjects;

public enum VehicleType {
    MOTO("Moto"),
    AUTO("Auto"),
    BICICLETA("Bicicleta"),
    PIE("Pie");

    private final String dbValue;

    VehicleType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static VehicleType fromDbValue(String value) {
        if (value == null) {
            return null;
        }
        for (VehicleType v : values()) {
            if (v.dbValue.equalsIgnoreCase(value)) {
                return v;
            }
        }
        return VehicleType.valueOf(value.toUpperCase());
    }
}