package com.flashdrop.reviews.domain.exception;

/**
 * El destino no puede derivarse de la orden (p. ej. se pide calificar al
 * repartidor de una orden que no tiene repartidor asignado). Se traduce a
 * HTTP 404.
 */
public class TargetNotFoundException extends RuntimeException {

    public TargetNotFoundException(String message) {
        super(message);
    }
}
