package com.flashdrop.catalog.application.usecase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.RatingAggregateClientPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRatingStatsRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.RatingAggregate;
import com.flashdrop.catalog.domain.model.RatingSummary;
import com.flashdrop.catalog.domain.model.RatingTargetType;
import com.flashdrop.catalog.domain.model.Restaurant;

/**
 * Trae los promedios de calificacion de reviews-service y los deja cacheados
 * en la base de catalog.
 *
 * <p>Via unica de sincronizacion: un pull por lotes programado cada TTL
 * (~5 min). Los lectores (marketplace y ficha del local) nunca llaman a
 * reviews-service: leen la cache. Si un lote falla, el valor cacheado anterior
 * se conserva y el fallo queda registrado.
 */
@Service
public class RefreshRestaurantRatingStatsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshRestaurantRatingStatsUseCase.class);

    private final RestaurantRepositoryPort restaurantRepositoryPort;
    private final RestaurantRatingStatsRepositoryPort statsRepositoryPort;
    private final RatingAggregateClientPort ratingAggregateClientPort;
    private final int batchSize;

    public RefreshRestaurantRatingStatsUseCase(
            RestaurantRepositoryPort restaurantRepositoryPort,
            RestaurantRatingStatsRepositoryPort statsRepositoryPort,
            RatingAggregateClientPort ratingAggregateClientPort,
            @Value("${catalog.reviews.batch-size:100}") int batchSize
    ) {
        this.restaurantRepositoryPort = restaurantRepositoryPort;
        this.statsRepositoryPort = statsRepositoryPort;
        this.ratingAggregateClientPort = ratingAggregateClientPort;
        this.batchSize = batchSize > 0 ? batchSize : 100;
    }

    /**
     * @return cuantos lotes se actualizaron y cuantos fallaron
     */
    public RefreshResult execute() {
        List<Long> restaurantIds = restaurantRepositoryPort.findAll().stream()
                .map(Restaurant::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        int refreshed = 0;
        int failed = 0;
        for (List<Long> batch : batches(restaurantIds)) {
            try {
                Map<Long, RatingAggregate> aggregates =
                        ratingAggregateClientPort.fetchAggregates(RatingTargetType.TIENDA, batch);
                statsRepositoryPort.replaceBatch(batch, toSummaries(aggregates));
                refreshed++;
            } catch (RuntimeException exception) {
                // Se conserva el ultimo valor cacheado del lote: la disponibilidad
                // del marketplace no depende de reviews-service.
                failed++;
                log.error("No se pudo actualizar el promedio de {} restaurantes; se conserva el valor cacheado: {}",
                        batch.size(), exception.getMessage());
            }
        }
        return new RefreshResult(refreshed, failed);
    }

    private Map<Long, RatingSummary> toSummaries(Map<Long, RatingAggregate> aggregates) {
        Map<Long, RatingSummary> summaries = new HashMap<>();
        aggregates.forEach((targetId, aggregate) ->
                summaries.put(targetId, new RatingSummary(aggregate.average(), aggregate.count())));
        return summaries;
    }

    private List<List<Long>> batches(List<Long> restaurantIds) {
        List<List<Long>> batches = new ArrayList<>();
        for (int start = 0; start < restaurantIds.size(); start += batchSize) {
            batches.add(restaurantIds.subList(start, Math.min(start + batchSize, restaurantIds.size())));
        }
        return batches;
    }

    /**
     * Resultado de un pull.
     *
     * @param refreshed lotes actualizados
     * @param failed    lotes que fallaron y conservaron el valor cacheado
     */
    public record RefreshResult(int refreshed, int failed) {
    }
}
