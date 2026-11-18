package com.flashdrop.catalog.domain.model;

/**
 * Promedio y cantidad de calificaciones de un destino.
 *
 * <p>Es el valor que catalog-service sirve al cliente. reviews-service es el
 * dueño del dato (S9) y catalog guarda en su propia base el ultimo agregado
 * que recibio, para no depender de la disponibilidad del otro servicio.
 *
 * @param average promedio de 1 a 5 estrellas; {@code null} cuando el destino
 *                todavia no tiene calificaciones
 * @param count   cantidad de calificaciones publicadas
 */
public record RatingSummary(Double average, long count) {

    /** Sin calificaciones: no hay promedio y el conteo es cero. */
    public static RatingSummary none() {
        return new RatingSummary(null, 0L);
    }
}
