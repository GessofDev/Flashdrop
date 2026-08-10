package com.flashdrop.auth.domain.exception;

public class EmailAlreadyRegisteredException extends DomainException {
    public EmailAlreadyRegisteredException() {
        super("AUTH_EMAIL_TAKEN", "El email o teléfono ya está registrado");
    }
}
