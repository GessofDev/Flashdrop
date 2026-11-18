package com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto;

import java.util.List;
import java.util.Map;

/**
 * Respuesta de {@code GET /api/reviews/{targetType}/{targetId}}: la página de
 * calificaciones públicas del destino más su agregado (S8).
 *
 * @param items         calificaciones públicas de la página.
 * @param page          número de página (0-based).
 * @param size          tamaño de página.
 * @param totalElements cantidad total de calificaciones públicas del destino.
 * @param totalPages    cantidad total de páginas.
 * @param aggregate     promedio, cantidad y distribución del destino.
 */
public record TargetReviewsResponse(
        List<ReviewResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        RatingAggregateResponse aggregate
) {

    /**
     * Agregado público de un destino.
     *
     * @param average     promedio de las calificaciones públicas.
     * @param count       cantidad de calificaciones públicas.
     * @param distribution cantidad de calificaciones por nota (claves "1".."5").
     */
    public record RatingAggregateResponse(
            double average,
            long count,
            Map<String, Long> distribution
    ) {
    }
}
