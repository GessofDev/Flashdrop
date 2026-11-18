package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity;

import java.time.OffsetDateTime;

import com.flashdrop.catalog.domain.model.RatingSummary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cache local del promedio de calificacion de un restaurante.
 *
 * <p>La tabla la escribio el pull contra reviews-service; el marketplace la
 * lee para no depender de la disponibilidad de ese servicio.
 */
@Entity
@Table(name = "restaurant_rating_stats")
public class RestaurantRatingStatsEntity {

    @Id
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "average")
    private Double average;

    /** La columna se llama `count` (contrato de la tabla); el property evita la colision con la funcion agregada. */
    @Column(name = "count")
    private Long ratingCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RestaurantRatingStatsEntity() {
    }

    public RatingSummary toDomain() {
        return new RatingSummary(average, ratingCount == null ? 0L : ratingCount);
    }

    public Long getRestaurantId() {
        return restaurantId;
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
