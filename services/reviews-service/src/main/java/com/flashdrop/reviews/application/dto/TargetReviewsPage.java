package com.flashdrop.reviews.application.dto;

import com.flashdrop.reviews.domain.model.Review;

import java.util.List;

/**
 * Página de calificaciones de un destino, con el agregado público (S8).
 *
 * @param items       calificaciones públicas de la página.
 * @param page        número de página (0-based).
 * @param size        tamaño de página.
 * @param totalElements cantidad total de calificaciones públicas del destino.
 * @param totalPages  cantidad total de páginas.
 * @param average     promedio de las calificaciones públicas del destino.
 * @param count       cantidad de calificaciones públicas del destino.
 * @param distribution cantidad de calificaciones por nota (1..5).
 */
public record TargetReviewsPage(
        List<Review> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        double average,
        long count,
        java.util.Map<Integer, Long> distribution
) {
}
