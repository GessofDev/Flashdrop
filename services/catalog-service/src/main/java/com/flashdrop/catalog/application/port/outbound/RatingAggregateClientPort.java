package com.flashdrop.catalog.application.port.outbound;

import java.util.List;
import java.util.Map;

import com.flashdrop.catalog.domain.model.RatingAggregate;
import com.flashdrop.catalog.domain.model.RatingTargetType;

/**
 * Cliente del endpoint interno de agregados de reviews-service.
 *
 * <p>Contrato: {@code GET /api/internal/reviews/aggregates?targetType=&ids=}
 * con header {@code X-Internal-Api-Key}, que responde
 * {@code {"items":[{"targetId":1,"average":4.5,"count":12}]}}.
 */
public interface RatingAggregateClientPort {

    /**
     * Pede los agregados de los destinos indicados.
     *
     * @return los agregados indexados por id de destino; vacio si el destino no
     *         tiene calificaciones
     * @throws com.flashdrop.catalog.domain.exception.RatingAggregatesUnavailableException
     *         si el servicio no respondio
     */
    Map<Long, RatingAggregate> fetchAggregates(RatingTargetType targetType, List<Long> targetIds);
}
