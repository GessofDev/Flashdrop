package com.flashdrop.catalog.domain.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.flashdrop.catalog.domain.model.Restaurant;

/** Geographic radius, using the same two-decimal haversine distance as Orders. */
public final class DeliveryCoverage {
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private DeliveryCoverage() {
    }

    public static boolean covers(Restaurant restaurant, Coordinates destination) {
        BigDecimal radius = restaurant.getDeliveryRadiusKm();
        if (radius == null || radius.compareTo(BigDecimal.ONE) < 0
                || radius.compareTo(BigDecimal.valueOf(50)) > 0 || restaurant.getLatitude() == null
                || restaurant.getLongitude() == null) {
            return false;
        }
        double lat1 = Math.toRadians(restaurant.getLatitude().doubleValue());
        double lat2 = Math.toRadians(destination.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(destination.longitude().doubleValue()
                - restaurant.getLongitude().doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        BigDecimal km = BigDecimal.valueOf(2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a))))
                .setScale(2, RoundingMode.HALF_UP);
        return km.compareTo(radius) <= 0;
    }
}
