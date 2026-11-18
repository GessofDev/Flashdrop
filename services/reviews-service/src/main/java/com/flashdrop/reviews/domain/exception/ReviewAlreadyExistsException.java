package com.flashdrop.reviews.domain.exception;

/**
 * Ya existe una calificación para esa terna (orden, autor, tipo de destino)
 * (S6). Se traduce a HTTP 409.
 *
 * <p>Es la garantía de idempotencia: un reintento de red devuelve conflicto y
 * no una fila nueva.</p>
 */
public class ReviewAlreadyExistsException extends RuntimeException {

    public ReviewAlreadyExistsException(Long orderId, Long authorUserId, String targetType) {
        super("Ya existe una calificacion para la orden " + orderId + ", el destino " + targetType
                + " y el autor " + authorUserId + ".");
    }
}
