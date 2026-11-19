package cl.flashdrop.orders.domain.model;

import java.math.BigDecimal;

/**
 * Costo de envio de un pedido y como se calculo.
 *
 * <p>{@code defaultFee = true} indica que se cobro el envio de respaldo por no haber tarifa;
 * vehiculo, dimension y desglose no aplican. La cobertura se valida incluso en ese caso.</p>
 */
public record DeliveryQuote(BigDecimal deliveryFee, Vehicle vehicle, Integer dimension, BigDecimal distanceKm,
                            BigDecimal dimensionCost, BigDecimal distanceCost, boolean defaultFee) {

    public static DeliveryQuote fallback(BigDecimal fee, BigDecimal distanceKm) {
        return new DeliveryQuote(fee, null, null, distanceKm, null, null, true);
    }
}
