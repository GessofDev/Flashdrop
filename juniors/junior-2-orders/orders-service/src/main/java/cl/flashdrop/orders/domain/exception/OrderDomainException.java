package cl.flashdrop.orders.domain.exception;

/**
 * Excepción de reglas de negocio del dominio de Pedidos.
 * No contiene dependencias de framework: es una RuntimeException pura.
 */
public class OrderDomainException extends RuntimeException {

    public OrderDomainException(String message) {
        super(message);
    }

    public OrderDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
