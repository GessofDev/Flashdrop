package com.flashdrop.delivery.application.usecase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRatingStatsRepository;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.RatingAggregateClientPort;
import com.flashdrop.delivery.application.port.outbound.RatingAggregate;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.model.RatingTargetType;
import com.flashdrop.delivery.domain.valueobjects.RatingSummary;

/**
 * Trae los promedios de calificacion de reviews-service y los deja cacheados
 * en la base de delivery.
 *
 * <p>Via unica de sincronizacion: un pull por lotes programado cada TTL
 * (~5 min). Los lectores (perfil del repartidor) nunca llaman a
 * reviews-service: leen la cache. Si un lote falla, el valor cacheado anterior
 * se conserva y el fallo queda registrado.
 *
 * <p>El destino de un repartidor en reviews-service es su id en auth
 * ({@code target_type=REPARTIDOR}), que es el valor que
 * {@code internal.delivery_persons.user_id} guarda como texto.
 */
@Service
public class RefreshDeliveryPersonRatingStatsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefreshDeliveryPersonRatingStatsUseCase.class);

    private final DeliveryPersonRepository deliveryPersonRepository;
    private final DeliveryPersonRatingStatsRepository statsRepository;
    private final RatingAggregateClientPort ratingAggregateClientPort;
    private final int batchSize;

    public RefreshDeliveryPersonRatingStatsUseCase(
            DeliveryPersonRepository deliveryPersonRepository,
            DeliveryPersonRatingStatsRepository statsRepository,
            RatingAggregateClientPort ratingAggregateClientPort,
            @Value("${delivery.reviews.batch-size:100}") int batchSize
    ) {
        this.deliveryPersonRepository = deliveryPersonRepository;
        this.statsRepository = statsRepository;
        this.ratingAggregateClientPort = ratingAggregateClientPort;
        this.batchSize = batchSize > 0 ? batchSize : 100;
    }

    /**
     * @return cuantos lotes se actualizaron y cuantos fallaron
     */
    public RefreshResult execute() {
        List<Long> courierUserIds = deliveryPersonRepository.findAll().stream()
                .map(DeliveryPerson::getUserId)
                .map(this::toAuthUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        int refreshed = 0;
        int failed = 0;
        for (List<Long> batch : batches(courierUserIds)) {
            try {
                Map<Long, RatingAggregate> aggregates =
                        ratingAggregateClientPort.fetchAggregates(RatingTargetType.REPARTIDOR, batch);
                statsRepository.replaceBatch(batch, toSummaries(aggregates));
                refreshed++;
            } catch (RuntimeException exception) {
                // Se conserva el ultimo valor cacheado del lote: la
                // disponibilidad del perfil del repartidor no depende de
                // reviews-service.
                failed++;
                log.error("No se pudo actualizar el promedio de {} repartidores; se conserva el valor cacheado: {}",
                        batch.size(), exception.getMessage());
            }
        }
        return new RefreshResult(refreshed, failed);
    }

    /**
     * El id del repartidor en auth. Es el destino que reviews-service usa
     * para el tipo REPARTIDOR, asi que es el que se le pide.
     */
    private Long toAuthUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException exception) {
            log.warn("El userId del repartidor no es un id numerico de auth; queda fuera del pull de promedios: {}",
                    userId);
            return null;
        }
    }

    private Map<Long, RatingSummary> toSummaries(Map<Long, RatingAggregate> aggregates) {
        Map<Long, RatingSummary> summaries = new HashMap<>();
        aggregates.forEach((targetId, aggregate) ->
                summaries.put(targetId, new RatingSummary(aggregate.average(), aggregate.count())));
        return summaries;
    }

    private List<List<Long>> batches(List<Long> courierUserIds) {
        List<List<Long>> batches = new ArrayList<>();
        for (int start = 0; start < courierUserIds.size(); start += batchSize) {
            batches.add(courierUserIds.subList(start, Math.min(start + batchSize, courierUserIds.size())));
        }
        return batches;
    }

    /**
     * Resultado de un pull.
     *
     * @param refreshed lotes actualizados
     * @param failed    lotes que fallaron y conservaron el valor cacheado
     */
    public record RefreshResult(int refreshed, int failed) {
    }
}
