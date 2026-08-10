package com.flashdrop.auth.domain.exception;

public class InvalidTokenException extends DomainException {
    public InvalidTokenException() {
        super("AUTH_TOKEN_INVALID", "Token inválido o expirado");
    }
}
