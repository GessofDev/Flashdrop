package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;

import java.util.Locale;

/**
 * Modo de recepción elegido por el cliente al crear el pedido. No se puede cambiar después.
 *
 * <ul>
 *   <li>{@link #SEGURO}: el pedido solo se entrega con el código único del cliente.</li>
 *   <li>{@link #NORMAL}: el pedido se puede dejar en recepción o con un tercero; el código es
 *       siempre {@link Order#DEFAULT_DELIVERY_CODE} y la app del repartidor lo completa sola.</li>
 * </ul>
 */
public enum ReceptionMode {

    NORMAL,
    SEGURO;

    /** Si el cliente no informa modo se asume NORMAL (retrocompatible con la app actual). */
    public static ReceptionMode fromRequest(String raw) {
        if (raw == null || raw.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new OrderDomainException("Modo de recepcion invalido: use NORMAL o SEGURO");
        }
    }
}
