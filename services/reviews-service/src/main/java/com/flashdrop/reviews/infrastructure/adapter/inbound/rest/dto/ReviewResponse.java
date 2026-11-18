package com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto;

import com.flashdrop.reviews.domain.model.Review;

import java.time.OffsetDateTime;

/**
 * Representación pública de una calificación.
 *
 * @param id           id de la calificación.
 * @param orderId      orden calificada.
 * @param authorUserId autor de la calificación.
 * @param targetType   tipo de destino.
 * @param targetId     id del destino.
 * @param rating       nota de 1 a 5 estrellas.
 * @param comment      comentario (texto plano) o {@code null}.
 * @param status       estado de publicación.
 * @param createdAt    instante de creación.
 */
public record ReviewResponse(
        Long id,
        Long orderId,
        Long authorUserId,
        String targetType,
        Long targetId,
        int rating,
        String comment,
        String status,
        OffsetDateTime createdAt
) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getOrderId(),
                review.getAuthorUserId(),
                review.getTargetType().name(),
                review.getTargetId(),
                review.getRating().value(),
                review.getComment(),
                review.getStatus().name(),
                review.getCreatedAt());
    }
}
