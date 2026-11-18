package com.flashdrop.delivery.application.dto;

import java.time.Instant;

/**
 * Perfil de un repartidor.
 *
 * @param ratingAverage promedio de 1 a 5 estrellas cacheado; {@code null}
 *                      cuando el repartidor todavia no tiene calificaciones
 * @param ratingCount   cantidad de calificaciones publicadas
 */
public record DeliveryPersonResponse(
        Long id,
        String userId,
        String vehicle,
        Instant createdAt,
        Double ratingAverage,
        long ratingCount
) {
}