package cl.flashdrop.orders.domain.model;

/** Por que se devuelve el dinero. Los valores coinciden con el CHECK de {@code refunds.reason}. */
public enum RefundReason {
    RECHAZO,
    CANCELACION,
    VENCIMIENTO,
    F17,
    F18,
    CLIENTE_AUSENTE
}
