package com.flashdrop.delivery.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRatingStatsRepository;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.RatingAggregate;
import com.flashdrop.delivery.application.port.outbound.RatingAggregateClientPort;
import com.flashdrop.delivery.domain.exception.RatingAggregatesUnavailableException;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.model.RatingTargetType;
import com.flashdrop.delivery.domain.valueobjects.RatingSummary;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;

/**
 * Pull de los promedios de calificacion de los repartidores (T15).
 *
 * <p>Las reglas que se comprueban:
 * <ul>
 *   <li>el destino que se le pide a reviews-service es el id del repartidor en
 *       auth, con {@code targetType=REPARTIDOR};</li>
 *   <li>el agregado recibido deja cacheado el promedio y el conteo;</li>
 *   <li>un pull fallido NO borra el ultimo valor cacheado;</li>
 *   <li>un repartidor cuyo userId no es un id numerico de auth queda fuera.</li>
 * </ul>
 */
@DisplayName("RefreshDeliveryPersonRatingStatsUseCase — pull de promedios de calificacion")
class RefreshDeliveryPersonRatingStatsUseCaseTest {

    private final DeliveryPersonRepository deliveryPersonRepository = mock(DeliveryPersonRepository.class);
    private final RatingAggregateClientPort ratingAggregateClientPort = mock(RatingAggregateClientPort.class);
    private final FakeStatsRepository statsRepository = new FakeStatsRepository();

    private RefreshDeliveryPersonRatingStatsUseCase useCase(int batchSize) {
        return new RefreshDeliveryPersonRatingStatsUseCase(
                deliveryPersonRepository, statsRepository, ratingAggregateClientPort, batchSize);
    }

    private static DeliveryPerson courier(String userId) {
        return new DeliveryPerson(1L, userId, VehicleType.MOTO, Instant.now());
    }

    @Test
    @DisplayName("pide el agregado de REPARTIDOR con el id del repartidor en auth")
    void pull_asksForTheCourierAuthId() {
        when(deliveryPersonRepository.findAll()).thenReturn(List.of(courier("33")));
        when(ratingAggregateClientPort.fetchAggregates(eq(RatingTargetType.REPARTIDOR), any()))
                .thenReturn(Map.of(33L, new RatingAggregate(33L, 4.5, 12L)));

        useCase(100).execute();

        verify(ratingAggregateClientPort).fetchAggregates(RatingTargetType.REPARTIDOR, List.of(33L));
        assertThat(statsRepository.findByCourierUserIds(List.of(33L)))
                .containsEntry(33L, new RatingSummary(4.5, 12L));
    }

    @Test
    @DisplayName("un pull fallido conserva el ultimo valor cacheado y no propaga la falla")
    void pull_fails_keepsTheLastCachedValue() {
        statsRepository.replaceBatch(List.of(33L), Map.of(33L, new RatingSummary(3.0, 4L)));
        when(deliveryPersonRepository.findAll()).thenReturn(List.of(courier("33")));
        when(ratingAggregateClientPort.fetchAggregates(eq(RatingTargetType.REPARTIDOR), any()))
                .thenThrow(new RatingAggregatesUnavailableException("reviews-service caido", new RuntimeException()));

        assertThatCode(() -> useCase(100).execute()).doesNotThrowAnyException();

        assertThat(statsRepository.findByCourierUserIds(List.of(33L)))
                .containsEntry(33L, new RatingSummary(3.0, 4L));
    }

    @Test
    @DisplayName("un repartidor con userId no numerico queda fuera del pull")
    void pull_skipsCouriersWithoutNumericAuthId() {
        when(deliveryPersonRepository.findAll()).thenReturn(List.of(courier("U1"), courier("33")));
        when(ratingAggregateClientPort.fetchAggregates(eq(RatingTargetType.REPARTIDOR), any()))
                .thenReturn(Map.of());

        useCase(100).execute();

        verify(ratingAggregateClientPort).fetchAggregates(RatingTargetType.REPARTIDOR, List.of(33L));
    }

    @Test
    @DisplayName("el pull se hace por lotes del tamanho configurado")
    void pull_batchesTheRequests() {
        when(deliveryPersonRepository.findAll())
                .thenReturn(List.of(courier("1"), courier("2"), courier("3")));
        when(ratingAggregateClientPort.fetchAggregates(eq(RatingTargetType.REPARTIDOR), any()))
                .thenReturn(Map.of());

        RefreshDeliveryPersonRatingStatsUseCase.RefreshResult result = useCase(2).execute();

        verify(ratingAggregateClientPort, times(2)).fetchAggregates(eq(RatingTargetType.REPARTIDOR), any());
        assertThat(result.refreshed()).isEqualTo(2);
        assertThat(result.failed()).isZero();
    }

    /**
     * Cache en memoria: permite observar el estado del port sin Mockito.
     */
    private static final class FakeStatsRepository implements DeliveryPersonRatingStatsRepository {

        private final Map<Long, RatingSummary> stats = new LinkedHashMap<>();

        @Override
        public Map<Long, RatingSummary> findByCourierUserIds(Collection<Long> courierUserIds) {
            Map<Long, RatingSummary> found = new LinkedHashMap<>();
            courierUserIds.forEach(id -> {
                RatingSummary summary = stats.get(id);
                if (summary != null) {
                    found.put(id, summary);
                }
            });
            return found;
        }

        @Override
        public void replaceBatch(Collection<Long> courierUserIds, Map<Long, RatingSummary> aggregates) {
            courierUserIds.forEach(id -> {
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
