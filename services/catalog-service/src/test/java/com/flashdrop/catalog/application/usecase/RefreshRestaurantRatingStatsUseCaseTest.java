package com.flashdrop.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flashdrop.catalog.application.port.outbound.RatingAggregateClientPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRatingStatsRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.RatingAggregatesUnavailableException;
import com.flashdrop.catalog.domain.model.RatingAggregate;
import com.flashdrop.catalog.domain.model.RatingSummary;
import com.flashdrop.catalog.domain.model.RatingTargetType;
import com.flashdrop.catalog.domain.model.Restaurant;

/**
 * Pull de los promedios de calificacion (T14).
 *
 * <p>Las reglas que se comprueban:
 * <ul>
 *   <li>el agregado recibido deja cacheado el promedio y el conteo;</li>
 *   <li>un pull fallido NO borra el ultimo valor cacheado (el marketplace
 *       sigue respondiendo con el promedio que ya se tenia);</li>
 *   <li>los restaurantes se piden por lotes.</li>
 * </ul>
 */
@DisplayName("RefreshRestaurantRatingStatsUseCase — pull de promedios de calificacion")
class RefreshRestaurantRatingStatsUseCaseTest {

    private final RestaurantRepositoryPort restaurantRepositoryPort = mock(RestaurantRepositoryPort.class);
    private final RatingAggregateClientPort ratingAggregateClientPort = mock(RatingAggregateClientPort.class);
    private final FakeStatsRepository statsRepository = new FakeStatsRepository();

    private RefreshRestaurantRatingStatsUseCase useCase(int batchSize) {
        return new RefreshRestaurantRatingStatsUseCase(
                restaurantRepositoryPort, statsRepository, ratingAggregateClientPort, batchSize);
    }

    private static Restaurant restaurant(Long id) {
        return new Restaurant(id, id, "Local " + id, "Calle " + id, LocalDateTime.now());
    }

    @Test
    @DisplayName("el agregado recibido queda cacheado con su promedio y su conteo")
    void pull_succeeds_cachesTheReceivedAggregates() {
        when(restaurantRepositoryPort.findAll()).thenReturn(List.of(restaurant(1L), restaurant(2L)));
        when(ratingAggregateClientPort.fetchAggregates(eq(RatingTargetType.TIENDA), any()))
                .thenReturn(Map.of(1L, new RatingAggregate(1L, 4.5, 12L)));

        RefreshRestaurantRatingStatsUseCase.RefreshResult result = useCase(100).execute();

        assertThat(result.refreshed()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(statsRepository.findByRestaurantIds(List.of(1L)))
                .containsEntry(1L, new RatingSummary(4.5, 12L));
    }

    @Test
    @DisplayName("un pull fallido conserva el ultimo valor cacheado y no propaga la falla")
    void pull_fails_keepsTheLastCachedValue() {
        statsRepository.replaceBatch(List.of(1L), Map.of(1L, new RatingSummary(3.0, 4L)));
        when(restaurantRepositoryPort.findAll()).thenReturn(List.of(restaurant(1L)));
        when(ratingAggregateClientPort.fetchAggregates(eq(RatingTargetType.TIENDA), any()))
                .thenThrow(new RatingAggregatesUnavailableException("reviews-service caido", new RuntimeException()));

        assertThatCode(() -> useCase(100).execute()).doesNotThrowAnyException();

        assertThat(statsRepository.findByRestaurantIds(List.of(1L)))
                .containsEntry(1L, new RatingSummary(3.0, 4L));
    }

    @Test
    @DisplayName("el pull se hace por lotes del tamanho configurado")
    void pull_batchesTheRequests() {
        when(restaurantRepositoryPort.findAll()).thenReturn(List.of(restaurant(1L), restaurant(2L), restaurant(3L)));
        when(ratingAggregateClientPort.fetchAggregates(eq(RatingTargetType.TIENDA), any()))
                .thenReturn(Map.of());

        RefreshRestaurantRatingStatsUseCase.RefreshResult result = useCase(2).execute();

        verify(ratingAggregateClientPort, times(2)).fetchAggregates(eq(RatingTargetType.TIENDA), any());
        assertThat(result.refreshed()).isEqualTo(2);
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("un restaurante sin calificaciones queda sin cache, no con el promedio anterior")
    void pull_withoutRatingsForARestaurant_dropsItsCachedValue() {
        statsRepository.replaceBatch(List.of(1L, 2L), Map.of(
                1L, new RatingSummary(3.0, 4L),
                2L, new RatingSummary(5.0, 9L)));
        when(restaurantRepositoryPort.findAll()).thenReturn(List.of(restaurant(1L), restaurant(2L)));
        when(ratingAggregateClientPort.fetchAggregates(eq(RatingTargetType.TIENDA), any()))
                .thenReturn(Map.of(1L, new RatingAggregate(1L, 4.5, 12L)));

        useCase(100).execute();

        assertThat(statsRepository.findByRestaurantIds(List.of(1L)))
                .containsEntry(1L, new RatingSummary(4.5, 12L));
        assertThat(statsRepository.findByRestaurantIds(List.of(2L))).isEmpty();
    }

    /**
     * Cache en memoria: permite observar el estado del port sin Mockito.
     */
    private static final class FakeStatsRepository implements RestaurantRatingStatsRepositoryPort {

        private final Map<Long, RatingSummary> stats = new LinkedHashMap<>();

        @Override
        public Map<Long, RatingSummary> findByRestaurantIds(Collection<Long> restaurantIds) {
            Map<Long, RatingSummary> found = new LinkedHashMap<>();
            restaurantIds.forEach(id -> {
                RatingSummary summary = stats.get(id);
                if (summary != null) {
                    found.put(id, summary);
                }
            });
            return found;
        }

        @Override
        public void replaceBatch(Collection<Long> restaurantIds, Map<Long, RatingSummary> aggregates) {
            restaurantIds.forEach(id -> {
                RatingSummary summary = aggregates.get(id);
                if (summary == null) {
                    stats.remove(id);
                } else {
                    stats.put(id, summary);
                }
            });
        }
    }
}
