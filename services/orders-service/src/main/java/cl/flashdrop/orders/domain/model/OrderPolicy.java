package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Plazos configurables del pedido (FlashDrop 1.0 tiendas, spec 4.1). No estan en el codigo:
 * el administrador define los valores globales y cada tienda puede ajustar los suyos.
 *
 * <p>La fila global ({@code restaurantId == null}) trae los tres valores. La de una tienda
 * puede dejar cualquiera en {@code null} y entonces hereda el global ({@link #resolve}).</p>
 */
@Getter
@Builder
public class OrderPolicy {

    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 1440;

    /** {@code null} = politica global. */
    private final UUID restaurantId;
    /** Plazo para pagar un pedido. */
    private final Integer paymentExpirationMinutes;
    /** Plazo para que la tienda acepte un pedido ya pagado. */
    private final Integer storeResponseMinutes;
    /** Minutos desde la compra en que el cliente puede cancelar. 0 = no puede cancelar. */
    private final Integer customerCancelWindowMinutes;

    /** Valores efectivos, ya resueltos: sin nulos. */
    public record Effective(int paymentExpirationMinutes, int storeResponseMinutes,
                            int customerCancelWindowMinutes) {
    }

    /** Valida rangos (los mismos CHECK de la base) y que la politica global este completa. */
    public void validate() {
        requireRange("El plazo para pagar", paymentExpirationMinutes, MIN_MINUTES);
        requireRange("El plazo de respuesta de la tienda", storeResponseMinutes, MIN_MINUTES);
        requireRange("El plazo de cancelacion del cliente", customerCancelWindowMinutes, 0);
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

    /** Aplica los ajustes de la tienda (si hay) sobre la politica global, campo por campo. */
    public static Effective resolve(OrderPolicy global, OrderPolicy store) {
        return new Effective(
                pick(store == null ? null : store.paymentExpirationMinutes, global.paymentExpirationMinutes),
                pick(store == null ? null : store.storeResponseMinutes, global.storeResponseMinutes),
                pick(store == null ? null : store.customerCancelWindowMinutes, global.customerCancelWindowMinutes));
    }

    private static int pick(Integer storeValue, Integer globalValue) {
        return storeValue != null ? storeValue : globalValue;
    }
}
