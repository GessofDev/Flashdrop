package com.flashdrop.reviews.domain.model;

import java.util.Map;

/**
 * Agregado público de un destino (S8): promedio, cantidad de calificaciones
 * públicas y distribución por nota.
 *
 * <p>Es lo que consume el marketplace (promedio de la tienda) y el perfil del
 * repartidor, y lo que devuelve el endpoint interno por lotes.</p>
 *
 * @param targetId     id del destino.
 * @param average     promedio de las calificaciones publicadas (0.0 si no hay ninguna).
 * @param count        cantidad de calificaciones publicadas.
 * @param distribution cantidad de calificaciones por nota (clave 1..5).
 */
public record TargetRatingStats(
        Long targetId,
        double average,
        long count,
        Map<Integer, Long> distribution
) {

    /**
     * @param targetId id del destino sin calificaciones.
     * @return agregado vacío (promedio 0.0, count 0, distribución en ceros).
     */
    public static TargetRatingStats empty(Long targetId) {
        return new TargetRatingStats(targetId, 0.0d, 0L, Map.of());
    }
}
