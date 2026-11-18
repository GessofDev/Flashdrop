package com.flashdrop.reviews.domain.exception;

/**
 * La orden no existe en orders-service. Se traduce a HTTP 404.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Orden no encontrada: " + orderId);
    }
}
