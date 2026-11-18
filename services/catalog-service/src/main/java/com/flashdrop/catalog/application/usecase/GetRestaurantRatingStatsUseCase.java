package com.flashdrop.catalog.application.usecase;

import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RestaurantRatingStatsRepositoryPort;
import com.flashdrop.catalog.domain.model.RatingSummary;

/**
 * Consulta los promedios cacheados de un conjunto de restaurantes.
 *
 * <p>Nunca llama a reviews-service: el marketplace no depende de la
 * disponibilidad de otro servicio. Si el pull fallo, devuelve el ultimo valor
 * cacheado.
 */
@Service
public class GetRestaurantRatingStatsUseCase {

    private final RestaurantRatingStatsRepositoryPort statsRepositoryPort;

    public GetRestaurantRatingStatsUseCase(RestaurantRatingStatsRepositoryPort statsRepositoryPort) {
        this.statsRepositoryPort = statsRepositoryPort;
    }

    /**
     * @return los promedios indexados por id de restaurante; los que no tienen
     *         dato cacheado no aparecen en el mapa
     */
    public Map<Long, RatingSummary> execute(Collection<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return Map.of();
        }
        return statsRepositoryPort.findByRestaurantIds(restaurantIds);
    }
}
