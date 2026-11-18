package com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.reviews.domain.model.Review;
import com.flashdrop.reviews.domain.model.ReviewStatus;
import com.flashdrop.reviews.domain.model.TargetRatingStats;
import com.flashdrop.reviews.domain.valueobjects.Rating;
import com.flashdrop.reviews.domain.valueobjects.RatingTarget;
import com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa.entity.ReviewJpaEntity;
import com.flashdrop.reviews.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistencia de calificaciones contra Postgres real (T3): constraints,
 * índices y agregados.
 */
@SpringBootTest
@ActiveProfiles("reviews")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ReviewsRepositoryIT — constraints, índices y agregados en Postgres")
class ReviewsRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reviews_db")
            .withUsername("reviews_svc")
            .withPassword("reviews_svc_password");

    @Autowired
    SpringDataReviewRepository repository;

    @Autowired
    JpaReviewRepositoryAdapter adapter;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // La clave interna es obligatoria en perfiles distintos de `dev`.
        registry.add("internal.api.key", () -> "test-internal-key");
        registry.add("auth.jwks-uri", () -> "http://localhost:1/jwks.json");
        registry.add("auth.issuer", () -> "flashdrop-auth");
    }

    private ReviewJpaEntity entity(Long orderId, Long authorUserId, RatingTarget targetType,
                                    Long targetId, short rating, ReviewStatus status) {
        ReviewJpaEntity entity = new ReviewJpaEntity();
        entity.setOrderId(orderId);
        entity.setAuthorUserId(authorUserId);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setRating(rating);
        entity.setComment("comentario");
        entity.setStatus(status);
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    @Test
    @DisplayName("TC1: la UNIQUE (order_id, author_user_id, target_type) impide la segunda fila")
    void uniqueConstraintBlocksDuplicates() {
        repository.save(entity(9001L, 10L, RatingTarget.TIENDA, 7L, (short) 5, ReviewStatus.PUBLICADO));

        assertThatThrownBy(() -> repository.saveAndFlush(
                entity(9001L, 10L, RatingTarget.TIENDA, 7L, (short) 3, ReviewStatus.PUBLICADO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("TC2: la misma terna con otro tipo de destino sí se puede guardar")
    void sameOrderAndAuthorWithAnotherTargetIsAllowed() {
        repository.save(entity(9002L, 10L, RatingTarget.TIENDA, 7L, (short) 5, ReviewStatus.PUBLICADO));
        repository.saveAndFlush(entity(9002L, 10L, RatingTarget.REPARTIDOR, 42L, (short) 4, ReviewStatus.PUBLICADO));

        assertThat(repository.count()).isGreaterThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("TC3: el CHECK de rating rechaza una nota fuera de 1..5")
    void checkConstraintBlocksOutOfRangeRating() {
        assertThatThrownBy(() -> repository.saveAndFlush(
                entity(9003L, 10L, RatingTarget.TIENDA, 7L, (short) 6, ReviewStatus.PUBLICADO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("TC4: los índices por destino y por autor existen")
    void indexesExist() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'internal' "
                        + "AND tablename = 'reviews' "
                        + "AND indexname IN ('idx_reviews_target', 'idx_reviews_author')",
                Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("TC5: se leen las calificaciones por (target_type, target_id)")
    void readsByTarget() {
        repository.save(entity(9004L, 10L, RatingTarget.TIENDA, 77L, (short) 5, ReviewStatus.PUBLICADO));
        repository.save(entity(9004L, 11L, RatingTarget.TIENDA, 77L, (short) 3, ReviewStatus.PUBLICADO));
        repository.save(entity(9004L, 12L, RatingTarget.REPARTIDOR, 77L, (short) 4, ReviewStatus.PUBLICADO));
        repository.flush();

        List<Review> found = adapter.findPublishedByTarget(
                RatingTarget.TIENDA, 77L, PageRequest.of(0, 10));

        assertThat(found).hasSize(2);
        assertThat(found).allSatisfy(review ->
                assertThat(review.getTargetType()).isEqualTo(RatingTarget.TIENDA));
    }

    @Test
    @DisplayName("TC6: el agregado devuelve promedio, cantidad y distribución")
    void aggregateReturnsAverageCountAndDistribution() {
        repository.save(entity(9005L, 10L, RatingTarget.TIENDA, 88L, (short) 5, ReviewStatus.PUBLICADO));
        repository.save(entity(9006L, 11L, RatingTarget.TIENDA, 88L, (short) 3, ReviewStatus.PUBLICADO));
        repository.save(entity(9007L, 12L, RatingTarget.TIENDA, 88L, (short) 4, ReviewStatus.PUBLICADO));
        repository.save(entity(9008L, 13L, RatingTarget.TIENDA, 88L, (short) 1, ReviewStatus.PUBLICADO));
        repository.save(entity(9009L, 14L, RatingTarget.TIENDA, 88L, (short) 5, ReviewStatus.OCULTO));
        repository.flush();

        List<TargetRatingStats> aggregates = adapter.aggregatePublishedByTargets(
                RatingTarget.TIENDA, List.of(88L));

        assertThat(aggregates).hasSize(1);
        TargetRatingStats stats = aggregates.get(0);
        assertThat(stats.targetId()).isEqualTo(88L);
        assertThat(stats.count()).isEqualTo(4L);
        assertThat(stats.average()).isEqualTo(3.25d);
        assertThat(stats.distribution())
                .containsEntry(1, 1L)
                .containsEntry(3, 1L)
                .containsEntry(4, 1L)
                .containsEntry(5, 1L);
    }

    @Test
    @DisplayName("TC7: las calificaciones ocultas no entran en el listado público")
    void hiddenReviewsAreNotListed() {
        repository.save(entity(9010L, 10L, RatingTarget.TIENDA, 99L, (short) 5, ReviewStatus.PUBLICADO));
        repository.save(entity(9011L, 11L, RatingTarget.TIENDA, 99L, (short) 1, ReviewStatus.OCULTO));
        repository.flush();

        List<Review> found = adapter.findPublishedByTarget(
                RatingTarget.TIENDA, 99L, PageRequest.of(0, 10));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getRating().value()).isEqualTo(5);
        assertThat(adapter.countPublishedByTarget(RatingTarget.TIENDA, 99L)).isEqualTo(1L);
    }
}
