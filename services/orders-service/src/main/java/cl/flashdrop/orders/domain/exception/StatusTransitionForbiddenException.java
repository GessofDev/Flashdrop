package cl.flashdrop.orders.domain.exception;

/**
 * El usuario no está autorizado a aplicar un cambio de estado: su rol no puede fijar el
 * estado solicitado, o (rol Restaurante) el pedido pertenece a otro restaurante.
 *
 * <p>Se mapea a 403 FORBIDDEN, distinto de {@link OrderDomainException} con transición
 * inválida para el estado actual (409 CONFLICT). PR-orders-status-authz, spec FR-4.
 * Sin dependencias de framework: es una RuntimeException pura.</p>
 */
public class StatusTransitionForbiddenException extends RuntimeException {

    public StatusTransitionForbiddenException(String message) {
        super(message);
    }
}
