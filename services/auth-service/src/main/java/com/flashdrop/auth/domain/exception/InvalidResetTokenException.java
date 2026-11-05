package com.flashdrop.auth.domain.exception;

/** Mismo error para un enlace inexistente, vencido o ya usado (S-07). */
public class InvalidResetTokenException extends DomainException {
    public InvalidResetTokenException() {
        super("AUTH_RESET_TOKEN_INVALID", "El enlace no es válido o ya venció. Solicita uno nuevo.");
    }
}
