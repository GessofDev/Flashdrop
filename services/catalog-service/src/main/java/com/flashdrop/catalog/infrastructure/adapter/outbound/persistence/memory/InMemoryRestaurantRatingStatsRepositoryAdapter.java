package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.memory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.flashdrop.catalog.application.port.outbound.RestaurantRatingStatsRepositoryPort;
import com.flashdrop.catalog.domain.model.RatingSummary;

/**
 * Cache de promedios en memoria para los perfiles sin base de datos
 * ({@code local} y {@code supabase}).
 *
 * <p>Sin base no hay tabla donde dejar el promedio, asi que se guarda en
 * memoria: el comportamiento observable para el cliente es el mismo (promedio
 * cacheado, cero calificaciones cuando no hay dato).
 */
@Repository
@Profile("!postgres")
public class InMemoryRestaurantRatingStatsRepositoryAdapter implements RestaurantRatingStatsRepositoryPort {

    private final Map<Long, RatingSummary> stats = new ConcurrentHashMap<>();

    @Override
    public Map<Long, RatingSummary> findByRestaurantIds(Collection<Long> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RatingSummary> summaries = new LinkedHashMap<>();
        restaurantIds.forEach(restaurantId -> {
            RatingSummary summary = stats.get(restaurantId);
            if (summary != null) {
                summaries.put(restaurantId, summary);
            }
        });
        return summaries;
    }

    @Override
    public void replaceBatch(Collection<Long> restaurantIds, Map<Long, RatingSummary> aggregates) {
        if (restaurantIds == null || restaurantIds.isEmpty()) {
            return;
        }
        restaurantIds.forEach(restaurantId -> {
            RatingSummary summary = aggregates.get(restaurantId);
            if (summary == null) {
                stats.remove(restaurantId);
            } else {
                stats.put(restaurantId, summary);
            }
        });
    }
}
