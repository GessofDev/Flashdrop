package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.flashdrop.catalog.domain.model.OpeningHours;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class RestaurantHoursEmbeddable {

    // Short porque la columna es smallint y ddl-auto=validate exige el tipo exacto.
    @Column(name = "day_of_week", nullable = false)
    private Short dayOfWeek;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    protected RestaurantHoursEmbeddable() {
    }

    RestaurantHoursEmbeddable(OpeningHours hours) {
        this.dayOfWeek = (short) hours.day().getValue();
        this.opensAt = hours.opensAt();
        this.closesAt = hours.closesAt();
    }

    OpeningHours toDomain() {
        return new OpeningHours(DayOfWeek.of(dayOfWeek), opensAt, closesAt);
    }
}
