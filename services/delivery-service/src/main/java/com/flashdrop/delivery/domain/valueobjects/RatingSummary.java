package com.flashdrop.delivery.domain.valueobjects;

/**
 * Promedio y cantidad de calificaciones de un repartidor.
 *
 * <p>Es el valor que delivery-service sirve al cliente. reviews-service es el
 * dueno del dato (S9) y delivery guarda en su propia base el ultimo agregado
 * que recibio, para no depender de la disponibilidad del otro servicio.
 *
 * @param average promedio de 1 a 5 estrellas; {@code null} cuando el
 *                repartidor todavia no tiene calificaciones
 * @param count   cantidad de calificaciones publicadas
 */
public record RatingSummary(Double average, long count) {

    /** Sin calificaciones: no hay promedio y el conteo es cero. */
    public static RatingSummary none() {
        return new RatingSummary(null, 0L);
    }
}
