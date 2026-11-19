package com.flashdrop.catalog.domain.valueobjects;

import java.math.BigDecimal;

public record Coordinates(BigDecimal latitude, BigDecimal longitude) {

    public static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    public static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    public static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    public static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    public Coordinates {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Both latitude and longitude are required");
        }
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }

    public static Coordinates ofNullable(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null && longitude == null) {
            return null;
        }
        return new Coordinates(latitude, longitude);
    }
}
