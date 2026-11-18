package com.flashdrop.reviews.domain.exception;

/**
 * El id de la orden no es válido (ausente o no numérico). Se traduce a HTTP 400.
 */
public class InvalidOrderIdException extends RuntimeException {

    public InvalidOrderIdException(String message) {
        super(message);
    }
}
