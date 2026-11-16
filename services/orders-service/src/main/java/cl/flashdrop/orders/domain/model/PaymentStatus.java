package cl.flashdrop.orders.domain.model;

/** Estado de un intento de cobro. Los valores se guardan tal cual en {@code payments.status}. */
public enum PaymentStatus {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    EXPIRADO
}
