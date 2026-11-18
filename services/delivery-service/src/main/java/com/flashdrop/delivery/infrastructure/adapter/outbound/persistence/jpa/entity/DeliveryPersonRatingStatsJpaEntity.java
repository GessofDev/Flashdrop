package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity;

import java.time.OffsetDateTime;

import com.flashdrop.delivery.domain.valueobjects.RatingSummary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cache local del promedio de calificacion de un repartidor.
 *
 * <p>La tabla la escribio el pull contra reviews-service; el perfil del
 * repartidor la lee para no depender de la disponibilidad de ese servicio.
 */
@Entity
@Table(name = "delivery_person_rating_stats", schema = "internal")
public class DeliveryPersonRatingStatsJpaEntity {

    /** Id del repartidor en auth (auth.users.id): el destino de reviews-service para REPARTIDOR. */
    @Id
    @Column(name = "courier_user_id")
    private Long courierUserId;

    @Column(name = "average")
    private Double average;

    /** La columna se llama `count` (contrato de la tabla); el property evita la colision con la funcion agregada. */
    @Column(name = "count")
    private Long ratingCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DeliveryPersonRatingStatsJpaEntity() {
    }

    public RatingSummary toDomain() {
        return new RatingSummary(average, ratingCount == null ? 0L : ratingCount);
    }

    public Long getCourierUserId() {
        return courierUserId;
    }

    public Double getAverage() {
        return average;
    }

    public Long getRatingCount() {
        return ratingCount;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
