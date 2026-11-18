package com.flashdrop.reviews.application.usecase;

import com.flashdrop.reviews.application.dto.TargetReviewsPage;
import com.flashdrop.reviews.application.port.inbound.ListReviewsForTargetUseCase;
import com.flashdrop.reviews.application.port.outbound.ReviewRepositoryPort;
import com.flashdrop.reviews.domain.model.Review;
import com.flashdrop.reviews.domain.model.TargetRatingStats;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Implementación de {@link ListReviewsForTargetUseCase}.
 *
 * <p>Solo expone calificaciones públicas (S8): las ocultas por moderación no
 * se listan ni entran en el promedio.</p>
 */
@Service
public class ListReviewsForTargetUseCaseImpl implements ListReviewsForTargetUseCase {

    private final ReviewRepositoryPort reviewRepository;

    public ListReviewsForTargetUseCaseImpl(ReviewRepositoryPort reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public TargetReviewsPage execute(RatingTarget targetType, Long targetId, Pageable pageable) {
        List<Review> items = reviewRepository.findPublishedByTarget(targetType, targetId, pageable);
        long total = reviewRepository.countPublishedByTarget(targetType, targetId);

        TargetRatingStats stats = reviewRepository
                .aggregatePublishedByTargets(targetType, List.of(targetId))
                .stream()
                .findFirst()
                .orElseGet(() -> TargetRatingStats.empty(targetId));

        Map<Integer, Long> distribution = stats.distribution();

        return new TargetReviewsPage(
                items,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                total,
                totalPages(total, pageable.getPageSize()),
                stats.average(),
                stats.count(),
                distribution);
    }

    private static int totalPages(long totalElements, int pageSize) {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / (double) pageSize);
    }
}
