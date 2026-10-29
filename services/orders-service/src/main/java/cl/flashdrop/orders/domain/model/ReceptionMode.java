package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;

import java.util.Locale;

/**
 * Modo de recepción del pedido. Los pedidos nuevos requieren recepción segura con PIN;
 * NORMAL se conserva únicamente para pedidos históricos.
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

    /** Si el cliente no informa modo, se aplica la política obligatoria de PIN. */
    public static ReceptionMode fromRequest(String raw) {
        if (raw == null || raw.isBlank()) {
            return SEGURO;
        }
        try {
            ReceptionMode mode = valueOf(raw.trim().toUpperCase(Locale.ROOT));
            if (mode != SEGURO) {
                throw new OrderDomainException("Todos los pedidos requieren recepción SEGURO con PIN");
            }
            return mode;
        } catch (IllegalArgumentException e) {
            throw new OrderDomainException("Modo de recepcion invalido: use NORMAL o SEGURO");
        }
    }
}
