package com.flashdrop.auth.domain.exception;

/** La persona autenticada no tiene permiso para la accion (HTTP 403). */
public class AccessForbiddenException extends DomainException {
    public AccessForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
