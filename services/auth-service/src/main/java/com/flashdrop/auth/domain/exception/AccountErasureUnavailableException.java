package com.flashdrop.auth.domain.exception;

/** Otro servicio no respondio al eliminar la cuenta (HTTP 503 al pedirlo; reintento automatico despues). */
public class AccountErasureUnavailableException extends DomainException {
    public AccountErasureUnavailableException(String message) {
        super("SERVICE_UNAVAILABLE", message);
    }

    public AccountErasureUnavailableException(String message, Throwable cause) {
        super("SERVICE_UNAVAILABLE", message);
        initCause(cause);
    }
}
