package cl.flashdrop.orders.domain.exception;

/**
 * El usuario autenticado no está autorizado a ejecutar la operación sobre ese recurso
 * (rol o ownership). Se mapea a 403 FORBIDDEN con un handler dedicado, sin depender del
 * matching por texto de {@link OrderDomainException}.
 *
 * <p>Sin dependencias de framework: es una RuntimeException pura, para que los casos de uso
 * no dependan de Spring Security.</p>
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
