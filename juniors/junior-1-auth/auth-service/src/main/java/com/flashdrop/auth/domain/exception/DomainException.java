package com.flashdrop.auth.domain.exception;

/**
 * Base de las excepciones de dominio. Cada una lleva un código estable
 * que el GlobalExceptionHandler mapea a la respuesta { code, service, trace_id, message }.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
