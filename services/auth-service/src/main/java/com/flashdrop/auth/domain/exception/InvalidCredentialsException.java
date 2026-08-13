package com.flashdrop.auth.domain.exception;

/**
 * S-07: mensaje genérico anti-enumeración. NO distingue entre
 * "email no existe" y "contraseña incorrecta".
 */
public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super("AUTH_INVALID_CREDENTIALS", "Credenciales inválidas");
    }
}
