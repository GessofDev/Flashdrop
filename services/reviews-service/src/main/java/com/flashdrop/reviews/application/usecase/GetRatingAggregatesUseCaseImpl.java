package com.flashdrop.reviews.application.usecase;

import com.flashdrop.reviews.application.port.inbound.GetRatingAggregatesUseCase;
import com.flashdrop.reviews.application.port.outbound.ReviewRepositoryPort;
import com.flashdrop.reviews.domain.model.TargetRatingStats;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación de {@link GetRatingAggregatesUseCase}.
 *
 * <p>Devuelve <b>un</b> elemento por cada id pedido, en el orden pedido: los
 * consumidores (catalog-service, delivery-service) pueden mapear por
 * {@code targetId} sin huecos por ids faltantes. Los ids sin calificaciones
 * vienen con {@code count = 0} y {@code average = 0.0}.</p>
 */
@Service
public class GetRatingAggregatesUseCaseImpl implements GetRatingAggregatesUseCase {

    private final ReviewRepositoryPort reviewRepository;

    public GetRatingAggregatesUseCaseImpl(ReviewRepositoryPort reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public List<TargetRatingStats> execute(RatingTarget targetType, List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }

        Map<Long, TargetRatingStats> found = new LinkedHashMap<>();
        for (TargetRatingStats stats : reviewRepository.aggregatePublishedByTargets(targetType, targetIds)) {
            found.put(stats.targetId(), stats);
        }

        return targetIds.stream()
                .map(targetId -> found.getOrDefault(targetId, TargetRatingStats.empty(targetId)))
                .toList();
    }
}
