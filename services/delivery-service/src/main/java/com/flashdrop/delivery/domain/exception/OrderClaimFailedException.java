package com.flashdrop.delivery.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Domain exception raised when delegation to orders-service's
 * {@code POST /api/internal/orders/claim} endpoint fails.
 *
 * <p>Carries the upstream {@link HttpStatus} so the global exception handler can
 * surface a meaningful error to the caller. The cause chain is preserved for
 * diagnostics (network failures wrap the underlying I/O exception; HTTP errors
 * wrap the upstream response).
 *
 * <p>PR-B invariant: this exception is thrown on EVERY non-2xx response. We do
 * not replicate the swallow-4xx/5xx anti-pattern from
 * {@code HttpOrderServiceClientAdapter.java:84-88} (separate delivery-side bug,
 * out of scope).
 */
public class OrderClaimFailedException extends RuntimeException {

    private final HttpStatus status;

    public OrderClaimFailedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public OrderClaimFailedException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
