package com.flashdrop.auth.domain.exception;

/**
 * El email solicitado ya pertenece a otra cuenta.
 *
 * <p>Distinta de {@link EmailAlreadyRegisteredException}: esta es el choque del
 * flujo de cambio de email, donde el mensaje no debe mentionar el telefono.
 */
public class EmailAlreadyInUseException extends DomainException {
    public EmailAlreadyInUseException() {
        super("EMAIL_ALREADY_EXISTS", "Ese email ya está en uso");
    }
}
