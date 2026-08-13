package com.flashdrop.auth.domain.exception;

public class InvalidUserException extends DomainException {
    public InvalidUserException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
