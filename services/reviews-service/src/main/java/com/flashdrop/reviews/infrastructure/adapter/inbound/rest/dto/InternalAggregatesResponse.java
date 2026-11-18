package com.flashdrop.reviews.infrastructure.adapter.inbound.rest.dto;

import java.util.List;

/**
 * Respuesta del endpoint interno {@code GET /api/internal/reviews/aggregates}.
 *
 * <p>Contrato consumido por catalog-service (promedio de la tienda) y
 * delivery-service (promedio del repartidor). No cambiar sin avisar.</p>
 *
 * @param items un agregado por cada id solicitado, en el orden pedido.
 */
public record InternalAggregatesResponse(List<RatingAggregateResponse> items) {

    /**
     * Agregado de un destino.
     *
     * @param targetId id del destino.
     * @param average promedio de las calificaciones públicas.
     * @param count   cantidad de calificaciones públicas.
     */
    public record RatingAggregateResponse(Long targetId, double average, long count) {
    }
}
