package com.flashdrop.catalog.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Tramo de atención de un día de la semana. Si {@code closesAt} es anterior a
 * {@code opensAt}, el tramo termina al día siguiente (22:00 a 02:00).
 */
public record OpeningHours(DayOfWeek day, LocalTime opensAt, LocalTime closesAt) {

    public OpeningHours {
        if (day == null || opensAt == null || closesAt == null) {
            throw new IllegalArgumentException("El dia y las horas del horario son obligatorios");
        }
        if (opensAt.equals(closesAt)) {
            throw new IllegalArgumentException("La hora de apertura y la de cierre no pueden ser iguales");
        }
    }

    public boolean covers(LocalDateTime moment) {
        DayOfWeek today = moment.getDayOfWeek();
        LocalTime time = moment.toLocalTime();

        if (opensAt.isBefore(closesAt)) {
            return today == day && !time.isBefore(opensAt) && time.isBefore(closesAt);
        }
        return (today == day && !time.isBefore(opensAt))
                || (today == day.plus(1) && time.isBefore(closesAt));
    }
}
