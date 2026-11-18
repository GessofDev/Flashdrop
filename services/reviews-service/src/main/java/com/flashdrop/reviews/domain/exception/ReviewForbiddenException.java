package com.flashdrop.reviews.domain.exception;

/**
 * El autor no está autorizado a calificar ese destino en esa orden: su rol
 * (claim {@code roles}) no habilita la relación, o no es participante de la
 * orden. Se traduce a HTTP 403.
 */
public class ReviewForbiddenException extends RuntimeException {

    public ReviewForbiddenException(String message) {
        super(message);
    }
}
