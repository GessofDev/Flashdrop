package com.flashdrop.reviews.domain.exception;

/**
 * Un id de destino (o un id del lote interno) no es numérico válido. Se
 * traduce a HTTP 400.
 */
public class InvalidTargetIdException extends RuntimeException {

    public InvalidTargetIdException(String message) {
        super(message);
    }
}
