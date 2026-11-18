package com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa.entity;

import com.flashdrop.reviews.domain.model.ReviewStatus;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Entidad JPA de {@code internal.reviews}.
 *
 * <p>Mapeo 1:1 con la migración {@code V1__reviews.sql}. Como el servicio usa
 * {@code ddl-auto: validate}, cualquier desalineación con el DDL falla en el
 * arranque del contexto: la base es la fuente de verdad del esquema.</p>
 *
 * <p>Tipos alineados con el DDL:</p>
 * <ul>
 *   <li>{@code rating SMALLINT} → {@link Short} (con {@code int} Hibernate
 *       esperaría INTEGER y la validación fallaría).</li>
 *   <li>{@code created_at/updated_at TIMESTAMPTZ} → {@link OffsetDateTime}.</li>
 * </ul>
 */
@Entity
@Table(name = "reviews")
public class ReviewJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private RatingTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "rating", nullable = false)
    private Short rating;

    @Column(name = "comment", length = 500)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReviewStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public ReviewJpaEntity() {
        // constructor para JPA
    }

    public ReviewJpaEntity(Long orderId,
                           Long authorUserId,
                           RatingTarget targetType,
                           Long targetId,
                           Short rating,
                           String comment,
                           ReviewStatus status,
                           OffsetDateTime createdAt,
                           OffsetDateTime updatedAt) {
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

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public void setAuthorUserId(Long authorUserId) {
        this.authorUserId = authorUserId;
    }

    public RatingTarget getTargetType() {
        return targetType;
    }

    public void setTargetType(RatingTarget targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public Short getRating() {
        return rating;
    }

    public void setRating(Short rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    void onInsert() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = ReviewStatus.PUBLICADO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
