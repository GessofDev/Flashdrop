package com.flashdrop.auth.domain.exception;

public class InvalidEmailChangeTokenException extends DomainException {
    public InvalidEmailChangeTokenException() {
        super("INVALID_EMAIL_CHANGE_TOKEN", "El enlace de cambio de email es inválido o expiró");
    }
}
