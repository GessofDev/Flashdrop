package cl.flashdrop.orders.domain.port;

import java.math.BigDecimal;

/**
 * Distancia entre dos coordenadas. Hoy es linea recta; cuando se contrate un servicio de mapas se
 * reemplaza este puerto sin tocar la tarifa.
 */
public interface DistanceCalculator {

    /** Kilometros entre los dos puntos, con dos decimales. */
    BigDecimal kmBetween(BigDecimal fromLatitude, BigDecimal fromLongitude,
                         BigDecimal toLatitude, BigDecimal toLongitude);
}
