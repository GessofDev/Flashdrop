package com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.reviews.application.port.outbound.ReviewRepositoryPort;
import com.flashdrop.reviews.domain.model.Review;
import com.flashdrop.reviews.domain.model.ReviewStatus;
import com.flashdrop.reviews.domain.model.TargetRatingStats;
import com.flashdrop.reviews.domain.valueobjects.Rating;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa.entity.ReviewJpaEntity;
import com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataReviewRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador JPA del puerto {@link ReviewRepositoryPort}.
 *
 * <p>Traduce entre la entidad de persistencia y el modelo de dominio, y aplica
 * el filtro de estado: las consultas públicas y los agregados solo cuentan
 * calificaciones {@link ReviewStatus#PUBLICADO} (S8).</p>
 */
@Component
public class JpaReviewRepositoryAdapter implements ReviewRepositoryPort {

    private final SpringDataReviewRepository repository;

    public JpaReviewRepositoryAdapter(SpringDataReviewRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Review save(Review review) {
        ReviewJpaEntity entity = toEntity(review);
        ReviewJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Review> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByOrderIdAndAuthorUserIdAndTargetType(Long orderId,
                                                                  Long authorUserId,
                                                                  RatingTarget targetType) {
        if (orderId == null || authorUserId == null || targetType == null) {
            return false;
        }
        return repository.existsByOrderIdAndAuthorUserIdAndTargetType(orderId, authorUserId, targetType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Review> findByOrderIdAndAuthorUserId(Long orderId, Long authorUserId) {
        if (orderId == null || authorUserId == null) {
            return List.of();
        }
        return repository.findByOrderIdAndAuthorUserIdOrderByCreatedAtDesc(orderId, authorUserId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Review> findPublishedByTarget(RatingTarget targetType, Long targetId, Pageable pageable) {
        if (targetType == null || targetId == null) {
            return List.of();
        }
        return repository.findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(
                        targetType, targetId, ReviewStatus.PUBLICADO, pageable)
                .getContent()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countPublishedByTarget(RatingTarget targetType, Long targetId) {
        if (targetType == null || targetId == null) {
            return 0L;
        }
        return repository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReviewStatus.PUBLICADO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Review> findPublishedByAuthor(Long authorUserId, Pageable pageable) {
        if (authorUserId == null) {
            return List.of();
        }
        return repository.findByAuthorUserIdAndStatusOrderByCreatedAtDesc(
                        authorUserId, ReviewStatus.PUBLICADO, pageable)
                .getContent()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countPublishedByAuthor(Long authorUserId) {
        if (authorUserId == null) {
            return 0L;
        }
        return repository.countByAuthorUserIdAndStatus(authorUserId, ReviewStatus.PUBLICADO);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TargetRatingStats> aggregatePublishedByTargets(RatingTarget targetType, List<Long> targetIds) {
        if (targetType == null || targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = targetIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Double> averages = new LinkedHashMap<>();
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : repository.aggregateByTargetIds(targetType, ReviewStatus.PUBLICADO, distinctIds)) {
            Long targetId = ((Number) row[0]).longValue();
            averages.put(targetId, ((Number) row[1]).doubleValue());
            counts.put(targetId, ((Number) row[2]).longValue());
        }

        Map<Long, Map<Integer, Long>> distributions = new LinkedHashMap<>();
        for (Object[] row : repository.distributionByTargetIds(targetType, ReviewStatus.PUBLICADO, distinctIds)) {
            Long targetId = ((Number) row[0]).longValue();
            int rating = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            distributions.computeIfAbsent(targetId, key -> new LinkedHashMap<>()).put(rating, count);
        }

        List<TargetRatingStats> result = new ArrayList<>(averages.size());
        for (Map.Entry<Long, Double> entry : averages.entrySet()) {
            Long targetId = entry.getKey();
            result.add(new TargetRatingStats(
                    targetId,
                    entry.getValue(),
                    counts.getOrDefault(targetId, 0L),
                    distributions.getOrDefault(targetId, Map.of())));
        }
        return List.copyOf(result);
    }

    private ReviewJpaEntity toEntity(Review review) {
        return new ReviewJpaEntity(
                review.getOrderId(),
                review.getAuthorUserId(),
                review.getTargetType(),
                review.getTargetId(),
                review.getRating() != null ? Short.valueOf((short) review.getRating().value()) : null,
                review.getComment(),
                review.getStatus() == null ? ReviewStatus.PUBLICADO : review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    private Review toDomain(ReviewJpaEntity entity) {
        return Review.persisted(
                entity.getId(),
                entity.getOrderId(),
                entity.getAuthorUserId(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getRating() == null ? null : Rating.of(entity.getRating()),
                entity.getComment(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
