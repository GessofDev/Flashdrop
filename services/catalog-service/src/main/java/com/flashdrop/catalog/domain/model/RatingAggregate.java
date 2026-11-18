package com.flashdrop.catalog.domain.model;

/**
 * Agregado de calificaciones de un destino, tal como lo devuelve
 * {@code GET /api/internal/reviews/aggregates} de reviews-service.
 *
 * @param targetId id del destino en el espacio de ids que corresponde al tipo
 *                 (TIENDA: catalog.restaurant.id)
 * @param average  promedio de 1 a 5 estrellas
 * @param count    cantidad de calificaciones publicadas
 */
public record RatingAggregate(Long targetId, Double average, long count) {
}
