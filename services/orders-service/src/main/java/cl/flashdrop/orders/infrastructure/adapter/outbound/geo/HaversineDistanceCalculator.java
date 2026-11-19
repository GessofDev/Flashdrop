package cl.flashdrop.orders.infrastructure.adapter.outbound.geo;

import cl.flashdrop.orders.domain.port.DistanceCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Distancia en linea recta sobre la esfera terrestre (formula de haversine). */
@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    @Override
    public BigDecimal kmBetween(BigDecimal fromLatitude, BigDecimal fromLongitude,
                                BigDecimal toLatitude, BigDecimal toLongitude) {
        double lat1 = Math.toRadians(fromLatitude.doubleValue());
        double lat2 = Math.toRadians(toLatitude.doubleValue());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(toLongitude.doubleValue() - fromLongitude.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }
}
