package com.flashdrop.delivery.domain.exception;

/**
 * El endpoint interno de agregados de reviews-service no respondio.
 *
 * <p>Se usa para distinguir "no hay datos" de "no se pudo consultar": el
 * consumidor que la lanza conserva el ultimo valor cacheado y sigue
 * sirviendo el perfil del repartidor.
 */
public class RatingAggregatesUnavailableException extends RuntimeException {

    public RatingAggregatesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
