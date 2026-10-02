package cl.flashdrop.orders.domain.exception;

/**
 * El usuario no está autorizado a aplicar un cambio de estado: su rol no puede fijar el
 * estado solicitado, o (rol Restaurante) el pedido pertenece a otro restaurante.
 *
 * <p>Se mapea a 403 FORBIDDEN (vía {@link ForbiddenOperationException}), distinto de
 * {@link OrderDomainException} con transición inválida para el estado actual (409 CONFLICT).
 * PR-orders-status-authz, spec FR-4.</p>
 */
public class StatusTransitionForbiddenException extends ForbiddenOperationException {

    public StatusTransitionForbiddenException(String message) {
        super(message);
    }
}
