package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.flashdrop.catalog.application.port.outbound.RestaurantRatingStatsRepositoryPort;
import com.flashdrop.catalog.domain.model.RatingSummary;
import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataRestaurantRatingStatsRepository;

/**
 * Cache de promedios sobre la base de catalog.
 *
 * <p>Es un snapshot por lote: lo que reviews-service devuelve es el estado
 * completo de los restaurantes del lote, asi que los que no vienen en la
 * respuesta quedan sin cache en vez de quedar pegados a un promedio viejo.
 */
@Repository
@Profile("postgres")
public class JpaRestaurantRatingStatsRepositoryAdapter implements RestaurantRatingStatsRepositoryPort {

    private final SpringDataRestaurantRatingStatsRepository repository;

    public JpaRestaurantRatingStatsRepositoryAdapter(SpringDataRestaurantRatingStatsRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<Long, RatingSummary> findByRestaurantIds(Collection<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RatingSummary> summaries = new LinkedHashMap<>();
        repository.findAllById(restaurantIds)
                .forEach(entity -> summaries.put(entity.getRestaurantId(), entity.toDomain()));
        return summaries;
    }

    @Override
    @Transactional
    public void replaceBatch(Collection<Long> restaurantIds, Map<Long, RatingSummary> aggregates) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return;
        }

        List<Long> withoutRatings = restaurantIds.stream()
                .filter(restaurantId -> !aggregates.containsKey(restaurantId))
                .toList();
        if (!withoutRatings.isEmpty()) {
            repository.deleteByRestaurantIds(withoutRatings);
        }

        aggregates.forEach((restaurantId, summary) -> {
            if (restaurantIds.contains(restaurantId)) {
                repository.upsert(restaurantId, summary.average(), summary.count());
            }
        });
    }
}
