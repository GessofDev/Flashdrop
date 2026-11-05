package com.flashdrop.catalog.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class RestaurantOpeningTest {

    // 2026-11-02 es lunes.
    private static final LocalDateTime LUNES_12 = LocalDateTime.of(2026, 11, 2, 12, 0);

    private static Restaurant local(boolean acceptingOrders, OpeningHours... hours) {
        return new Restaurant(1L, 1L, "Local", "Calle", null, acceptingOrders, List.of(hours));
    }

    private static OpeningHours tramo(DayOfWeek day, String opens, String closes) {
        return new OpeningHours(day, LocalTime.parse(opens), LocalTime.parse(closes));
    }

    @Test
    void sinHorarioDefinidoSeRigeSoloPorElInterruptor() {
        assertTrue(local(true).isOpenAt(LUNES_12));
        assertFalse(local(false).isOpenAt(LUNES_12));
    }

    @Test
    void elConstructorHistoricoDejaElLocalAbierto() {
        assertTrue(new Restaurant(1L, 1L, "Local", "Calle", null).isOpenAt(LUNES_12));
    }

    @Test
    void elInterruptorCerradoMandaSobreElHorario() {
        assertFalse(local(false, tramo(DayOfWeek.MONDAY, "09:00", "18:00")).isOpenAt(LUNES_12));
    }

    @Test
    void laAperturaIncluyeSuMinutoYElCierreNo() {
        Restaurant local = local(true, tramo(DayOfWeek.MONDAY, "09:00", "18:00"));

        assertFalse(local.isOpenAt(LUNES_12.withHour(8).withMinute(59)));
        assertTrue(local.isOpenAt(LUNES_12.withHour(9)));
        assertTrue(local.isOpenAt(LUNES_12.withHour(17).withMinute(59)));
        assertFalse(local.isOpenAt(LUNES_12.withHour(18)));
    }

    @Test
    void conHorarioUnDiaAusenteEstaCerrado() {
        Restaurant local = local(true, tramo(DayOfWeek.TUESDAY, "09:00", "18:00"));

        assertFalse(local.isOpenAt(LUNES_12));
        assertTrue(local.isOpenAt(LUNES_12.plusDays(1)));
    }

    @Test
    void unTramoNocturnoSigueAbiertoLaMadrugadaSiguiente() {
        Restaurant local = local(true, tramo(DayOfWeek.FRIDAY, "22:00", "02:00"));
        LocalDateTime viernes = LUNES_12.plusDays(4);

        assertFalse(local.isOpenAt(viernes.withHour(21)));
        assertTrue(local.isOpenAt(viernes.withHour(23)));
        assertTrue(local.isOpenAt(viernes.plusDays(1).withHour(1)));
        assertFalse(local.isOpenAt(viernes.plusDays(1).withHour(2)));
        // La madrugada del mismo viernes pertenece al jueves, que no atiende.
        assertFalse(local.isOpenAt(viernes.withHour(1)));
    }

    @Test
    void elTramoNocturnoDelDomingoTerminaElLunes() {
        Restaurant local = local(true, tramo(DayOfWeek.SUNDAY, "22:00", "02:00"));

        assertTrue(local.isOpenAt(LUNES_12.withHour(1)));
    }

    @Test
    void aperturaYCierreIgualesSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> tramo(DayOfWeek.MONDAY, "09:00", "09:00"));
    }
}
