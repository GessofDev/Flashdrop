package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.flashdrop.catalog.domain.model.OpeningHours;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {@code day} sigue ISO: 1 es lunes y 7 domingo. */
public record OpeningHoursDto(
        @NotNull @Min(1) @Max(7) Integer day,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closesAt
) {
    public static OpeningHoursDto fromDomain(OpeningHours hours) {
        return new OpeningHoursDto(hours.day().getValue(), hours.opensAt(), hours.closesAt());
    }

    public OpeningHours toDomain() {
        return new OpeningHours(DayOfWeek.of(day), opensAt, closesAt);
    }
}
