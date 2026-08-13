package com.flashdrop.auth.domain.exception;

public class WeakPasswordException extends DomainException {
    public WeakPasswordException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
