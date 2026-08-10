package com.flashdrop.auth.domain.exception;

/**
 * S-08: demasiados intentos. Se mapea a HTTP 429.
 */
public class RateLimitExceededException extends DomainException {
    public RateLimitExceededException() {
        super("AUTH_RATE_LIMITED", "Demasiados intentos. Espera un momento antes de reintentar");
    }
}
