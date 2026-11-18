package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRatingStatsRepository;
import com.flashdrop.delivery.domain.valueobjects.RatingSummary;

/**
 * Cache de promedios sobre la base de delivery.
 *
 * <p>Es un snapshot por lote: lo que reviews-service devuelve es el estado
 * completo de los repartidores del lote, asi que los que no vienen en la
 * respuesta quedan sin cache en vez de quedar pegados a un promedio viejo.
 */
@Component
public class JpaDeliveryPersonRatingStatsRepositoryAdapter implements DeliveryPersonRatingStatsRepository {

    private final JpaDeliveryPersonRatingStatsRepository repository;

    public JpaDeliveryPersonRatingStatsRepositoryAdapter(JpaDeliveryPersonRatingStatsRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<Long, RatingSummary> findByCourierUserIds(Collection<Long> courierUserIds) {
        if (courierUserIds == null || courierUserIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RatingSummary> summaries = new LinkedHashMap<>();
        repository.findAllById(courierUserIds)
                .forEach(entity -> summaries.put(entity.getCourierUserId(), entity.toDomain()));
        return summaries;
    }

    @Override
    @Transactional
    public void replaceBatch(Collection<Long> courierUserIds, Map<Long, RatingSummary> aggregates) {
        if (courierUserIds == null || courierUserIds.isEmpty()) {
            return;
        }

        List<Long> withoutRatings = courierUserIds.stream()
                .filter(courierUserId -> !aggregates.containsKey(courierUserId))
                .toList();
        if (!withoutRatings.isEmpty()) {
            repository.deleteByCourierUserIds(withoutRatings);
        }

        aggregates.forEach((courierUserId, summary) -> {
            if (courierUserIds.contains(courierUserId)) {
                repository.upsert(courierUserId, summary.average(), summary.count());
            }
        });
    }
}
