package com.flashdrop.auth.domain.exception;

public class InvalidEmailException extends DomainException {
    public InvalidEmailException(String value) {
        super("VALIDATION_ERROR", "Email inválido: " + value);
    }
}
