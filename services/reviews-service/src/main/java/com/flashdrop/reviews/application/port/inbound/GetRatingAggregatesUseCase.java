package com.flashdrop.reviews.application.port.inbound;

import com.flashdrop.reviews.domain.model.TargetRatingStats;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;

import java.util.List;

/**
 * Caso de uso: agregados de calificación por lotes (endpoint interno que
 * consumen catalog-service y delivery-service).
 */
public interface GetRatingAggregatesUseCase {

    /**
     * @param targetType tipo de destino (TIENDA | REPARTIDOR | CLIENTE).
     * @param targetIds  ids de destino consultados.
     * @return un agregado por cada id solicitado, en el orden pedido. Los ids
     *         sin calificaciones vienen con {@code count = 0} y
     *         {@code average = 0.0}.
     */
    List<TargetRatingStats> execute(RatingTarget targetType, List<Long> targetIds);
}
