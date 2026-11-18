package com.flashdrop.reviews.application.dto;

import com.flashdrop.reviews.domain.model.Review;

import java.util.List;

/**
 * Página de calificaciones escritas por el autor.
 *
 * @param items         calificaciones del autor en la página.
 * @param page          número de página (0-based).
 * @param size          tamaño de página.
 * @param totalElements cantidad total de calificaciones del autor.
 * @param totalPages    cantidad total de páginas.
 */
public record MyReviewsPage(
        List<Review> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
