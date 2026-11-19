package com.flashdrop.delivery.domain.valueobjects;

import java.math.BigDecimal;
import java.time.Instant;

public record CourierLocation(BigDecimal latitude, BigDecimal longitude, Instant recordedAt) {

    public static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    public static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    public static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    public static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    public CourierLocation {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("latitude and longitude are required");
        }
        if (recordedAt == null) {
            throw new IllegalArgumentException("recordedAt is required");
        }
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
    }
}
