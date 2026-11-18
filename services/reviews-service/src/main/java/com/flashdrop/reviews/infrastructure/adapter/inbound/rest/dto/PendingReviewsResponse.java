package com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto;

import java.util.List;

/**
 * Respuesta de {@code GET /api/reviews/pending/{orderId}}: qué falta calificar
 * y qué ya se calificó.
 *
 * @param orderId   id de la orden.
 * @param pending   destinos pendientes para el autor.
 * @param completed calificaciones ya escritas por el autor.
 */
public record PendingReviewsResponse(
        Long orderId,
        List<PendingTargetResponse> pending,
        List<ReviewResponse> completed
) {

    /**
     * Un destino pendiente.
     *
     * @param targetType tipo de destino.
     * @param targetId   id del destino (derivado de la orden).
     */
    public record PendingTargetResponse(String targetType, Long targetId) {
    }
}
