package com.flashdrop.reviews.domain.exception;

/**
 * La orden no está en estado {@code ENTREGADO} (S3): todavía no se puede
 * calificar. Se traduce a HTTP 422.
 */
public class OrderNotDeliveredException extends RuntimeException {

    public OrderNotDeliveredException(Long orderId, String status) {
        super("La orden " + orderId + " no esta entregada (estado actual: " + status + "). "
                + "Solo las ordenes entregadas pueden calificar.");
    }
}
