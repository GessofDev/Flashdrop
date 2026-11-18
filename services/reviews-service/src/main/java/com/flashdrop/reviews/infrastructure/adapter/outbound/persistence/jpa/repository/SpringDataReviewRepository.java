package com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa.repository;

import com.flashdrop.reviews.domain.model.ReviewStatus;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa.entity.ReviewJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio Spring Data de {@code internal.reviews}.
 *
 * <p>Los nombres de los métodos derivados dependen de los nombres de las
 * propiedades de {@link ReviewJpaEntity} ({@code orderId},
 * {@code authorUserId}, {@code targetType}, {@code targetId}, {@code status}).</p>
 */
@Repository
public interface SpringDataReviewRepository extends JpaRepository<ReviewJpaEntity, Long> {

    boolean existsByOrderIdAndAuthorUserIdAndTargetType(Long orderId, Long authorUserId, RatingTarget targetType);

    List<ReviewJpaEntity> findByOrderIdAndAuthorUserIdOrderByCreatedAtDesc(Long orderId, Long authorUserId);

    Page<ReviewJpaEntity> findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(
            RatingTarget targetType, Long targetId, ReviewStatus status, Pageable pageable);

    long countByTargetTypeAndTargetIdAndStatus(RatingTarget targetType, Long targetId, ReviewStatus status);

    Page<ReviewJpaEntity> findByAuthorUserIdAndStatusOrderByCreatedAtDesc(
            Long authorUserId, ReviewStatus status, Pageable pageable);

    long countByAuthorUserIdAndStatus(Long authorUserId, ReviewStatus status);

    /**
     * Promedio y cantidad por destino (S8).
     *
     * @return filas {@code [targetId, average, count]}.
     */
    @Query("select r.targetId, avg(r.rating), count(r) "
            + "from ReviewJpaEntity r "
            + "where r.targetType = :targetType and r.status = :status and r.targetId in :targetIds "
            + "group by r.targetId")
    List<Object[]> aggregateByTargetIds(@Param("targetType") RatingTarget targetType,
                                        @Param("status") ReviewStatus status,
                                        @Param("targetIds") List<Long> targetIds);

    /**
     * Distribución por nota (1..5) y destino.
     *
     * @return filas {@code [targetId, rating, count]}.
     */
    @Query("select r.targetId, r.rating, count(r) "
            + "from ReviewJpaEntity r "
            + "where r.targetType = :targetType and r.status = :status and r.targetId in :targetIds "
            + "group by r.targetId, r.rating")
    List<Object[]> distributionByTargetIds(@Param("targetType") RatingTarget targetType,
                                           @Param("status") ReviewStatus status,
                                           @Param("targetIds") List<Long> targetIds);
}
