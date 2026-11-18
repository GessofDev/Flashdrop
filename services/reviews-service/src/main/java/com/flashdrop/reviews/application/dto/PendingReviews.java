package com.flashdrop.reviews.application.dto;

import com.flashdrop.reviews.domain.model.Review;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;

import java.util.List;

/**
 * Respuesta del formulario de pendientes: qué calificaciones puede hacer el
 * autor todavía en esa orden y cuáles ya hizo.
 *
 * @param orderId   id de la orden.
 * @param pending   destinos que el autor todavía puede calificar.
 * @param completed calificaciones que el autor ya escribió en esa orden.
 */
public record PendingReviews(
        Long orderId,
        List<PendingTarget> pending,
        List<Review> completed
) {

    /**
     * Un destino pendiente: tipo de destino y su id (derivado de la orden).
     *
     * @param targetType tipo de destino.
     * @param targetId   id del destino.
     */
    public record PendingTarget(RatingTarget targetType, Long targetId) {
    }
}
