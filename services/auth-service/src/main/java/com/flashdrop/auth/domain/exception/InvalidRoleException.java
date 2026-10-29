package com.flashdrop.auth.domain.exception;

/**
 * El rol pedido no existe en la tabla {@code roles}, o el nombre de rol no
 * es utilizable (vacio o en blanco).
 */
public class InvalidRoleException extends DomainException {
    public InvalidRoleException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
