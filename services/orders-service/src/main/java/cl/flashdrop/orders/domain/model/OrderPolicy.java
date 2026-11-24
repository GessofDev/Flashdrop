package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Plazos configurables del pedido (FlashDrop 1.0 tiendas, spec 4.1). No estan en el codigo:
 * el administrador define los valores globales.
 *
 * <p>La fila global ({@code restaurantId == null}) trae los plazos. La de una tienda puede ajustar el plazo
 * para pagar y la ventana de cancelacion del cliente; la espera de repartidor es <b>solo global</b> (F7):
 * una tienda no la elige y si su fila trae un valor se ignora ({@link #resolve}). El plazo de respuesta de la
 * tienda no se aplica automaticamente; la aceptacion es manual.</p>
 */
@Getter
@Builder
public class OrderPolicy {

    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 1440;
    /** Espera de repartidor que se usa si la politica global aun no la define. */
    public static final int DEFAULT_COURIER_WAIT_MINUTES = 20;

    /** {@code null} = politica global. */
    private final UUID restaurantId;
    /** Plazo para pagar un pedido. */
    private final Integer paymentExpirationMinutes;
    /** Reservado: no hay vencimiento automatico de la aceptacion manual. La columna sigue en la base. */
    private final Integer storeResponseMinutes;
    /** Minutos desde la compra en que el cliente puede cancelar. 0 = no puede cancelar. */
    private final Integer customerCancelWindowMinutes;
    /** Minutos que se espera un repartidor desde que el pedido queda listo para retiro. Solo se lee de la politica global. */
    private final Integer courierWaitMinutes;

    /** Valores efectivos, ya resueltos: sin nulos. */
    public record Effective(int paymentExpirationMinutes, int storeResponseMinutes,
                            int customerCancelWindowMinutes, int courierWaitMinutes) {

        public Effective(int paymentExpirationMinutes, int storeResponseMinutes, int customerCancelWindowMinutes) {
            this(paymentExpirationMinutes, storeResponseMinutes, customerCancelWindowMinutes,
                    DEFAULT_COURIER_WAIT_MINUTES);
        }
    }

    /** Valida rangos (los mismos CHECK de la base) y que la politica global este completa. */
    public void validate() {
        requireRange("El plazo para pagar", paymentExpirationMinutes, MIN_MINUTES);
        requireRange("El plazo de respuesta de la tienda", storeResponseMinutes, MIN_MINUTES);
        requireRange("El plazo de cancelacion del cliente", customerCancelWindowMinutes, 0);
        requireRange("La espera de repartidor", courierWaitMinutes, MIN_MINUTES);
        if (restaurantId == null && (paymentExpirationMinutes == null || storeResponseMinutes == null
                || customerCancelWindowMinutes == null)) {
            throw new OrderDomainException("La politica global debe definir los tres plazos");
        }
    }

    private static void requireRange(String label, Integer value, int min) {
        if (value != null && (value < min || value > MAX_MINUTES)) {
            throw new OrderDomainException(label + " debe estar entre " + min + " y " + MAX_MINUTES + " minutos");
        }
    }

    /**
     * Aplica los ajustes de la tienda (si hay) sobre la politica global, campo por campo. El plazo de
     * respuesta de la tienda y la espera de repartidor salen siempre de la politica global.
     */
    public static Effective resolve(OrderPolicy global, OrderPolicy store) {
        return new Effective(
                pick(store == null ? null : store.paymentExpirationMinutes, global.paymentExpirationMinutes),
                global.storeResponseMinutes,
                pick(store == null ? null : store.customerCancelWindowMinutes, global.customerCancelWindowMinutes),
                global.courierWaitMinutes != null ? global.courierWaitMinutes : DEFAULT_COURIER_WAIT_MINUTES);
    }

    private static int pick(Integer storeValue, Integer globalValue) {
        return storeValue != null ? storeValue : globalValue;
    }
}
