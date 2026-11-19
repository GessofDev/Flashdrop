package cl.flashdrop.orders.domain.model;

import java.math.BigDecimal;

/**
 * Costo de envio de un pedido y como se calculo.
 *
 * <p>{@code defaultFee = true} indica que se cobro el envio de respaldo (no habia tarifa cargada o
 * el destino no traia coordenadas); en ese caso vehiculo, dimension y desglose no aplican.</p>
 */
public record DeliveryQuote(BigDecimal deliveryFee, Vehicle vehicle, Integer dimension, BigDecimal distanceKm,
                            BigDecimal dimensionCost, BigDecimal distanceCost, boolean defaultFee) {

    public static DeliveryQuote fallback(BigDecimal fee, BigDecimal distanceKm) {
        return new DeliveryQuote(fee, null, null, distanceKm, null, null, true);
    }
}
