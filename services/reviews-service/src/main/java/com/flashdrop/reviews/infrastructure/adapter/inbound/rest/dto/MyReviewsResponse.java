package com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto;

import java.util.List;

/**
 * Respuesta de {@code GET /api/users/me/reviews}: historial de las
 * calificaciones escritas por el autor.
 *
 * @param items         calificaciones del autor en la página.
 * @param page          número de página (0-based).
 * @param size          tamaño de página.
 * @param totalElements cantidad total de calificaciones del autor.
 * @param totalPages    cantidad total de páginas.
 */
public record MyReviewsResponse(
        List<ReviewResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
