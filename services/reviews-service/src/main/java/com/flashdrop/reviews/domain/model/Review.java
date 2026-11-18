package com.flashdrop.reviews.domain.model;

import com.flashdrop.reviews.domain.valueobjects.Rating;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;

import java.time.OffsetDateTime;

/**
 * Calificación de un actor sobre otro dentro de una orden.
 *
 * <p>Es la entidad que reviews-service persiste en su propia base
 * ({@code internal.reviews}). Es inmutable: una calificación escrita no se
 * edita, solo se oculta (moderación, fuera de v1).</p>
 *
 * <p>El {@code targetId} <b>nunca</b> viene del cliente: lo deriva el servidor
 * desde la orden (S7).</p>
 */
public class Review {

    private final Long id;
    private final Long orderId;
    private final Long authorUserId;
    private final RatingTarget targetType;
    private final Long targetId;
    private final Rating rating;
    private final String comment;
    private final ReviewStatus status;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private Review(Long id,
                   Long orderId,
                   Long authorUserId,
                   RatingTarget targetType,
                   Long targetId,
                   Rating rating,
                   String comment,
                   ReviewStatus status,
                   OffsetDateTime createdAt,
                   OffsetDateTime updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.authorUserId = authorUserId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.rating = rating;
        this.comment = comment;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Crea una calificación nueva (aún sin id persistido).
     *
     * @param orderId      orden calificada.
     * @param authorUserId autor (auth.users.id).
     * @param targetType   tipo de destino.
     * @param targetId     id del destino, derivado de la orden.
     * @param rating       nota de 1 a 5 estrellas.
     * @param comment      comentario opcional, ya sanitizado (puede ser {@code null}).
     * @param now          instante de creación.
     */
    public static Review create(Long orderId,
                                Long authorUserId,
                                RatingTarget targetType,
                                Long targetId,
                                Rating rating,
                                String comment,
                                OffsetDateTime now) {
        return new Review(null, orderId, authorUserId, targetType, targetId, rating,
                comment, ReviewStatus.PUBLICADO, now, now);
    }

    /**
     * Reconstruye una calificación leída de la base de datos.
     */
    public static Review persisted(Long id,
                                   Long orderId,
                                   Long authorUserId,
                                   RatingTarget targetType,
                                   Long targetId,
                                   Rating rating,
                                   String comment,
                                   ReviewStatus status,
                                   OffsetDateTime createdAt,
                                   OffsetDateTime updatedAt) {
        return new Review(id, orderId, authorUserId, targetType, targetId, rating,
                comment, status, createdAt, updatedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public RatingTarget getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Rating getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * @return {@code true} si la calificación es visible públicamente (S8).
     */
    public boolean isPublic() {
        return status != null && status.isPublic();
    }
}
