package com.flashdrop.delivery.application.usecase;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRatingStatsRepository;
import com.flashdrop.delivery.domain.valueobjects.RatingSummary;

/**
 * Consulta los promedios cacheados de un conjunto de repartidores.
 *
 * <p>Nunca llama a reviews-service: el perfil del repartidor no depende de la
 * disponibilidad de otro servicio. Si el pull fallo, devuelve el ultimo valor
 * cacheado.
 */
@Service
public class GetDeliveryPersonRatingStatsUseCase {

    private final DeliveryPersonRatingStatsRepository statsRepository;

    public GetDeliveryPersonRatingStatsUseCase(DeliveryPersonRatingStatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    /**
     * @return los promedios indexados por id de repartidor en auth; los que no
     *         tienen dato cacheado no aparecen en el mapa
     */
    public Map<Long, RatingSummary> execute(Collection<Long> courierUserIds) {
        if (courierUserIds == null || courierUserIds.isEmpty()) {
            return Map.of();
        }
        return statsRepository.findByCourierUserIds(courierUserIds);
    }

    /**
     * Promedio de un repartidor a partir del id que guarda delivery
     * ({@code delivery_persons.user_id}), que es su id en auth.
     *
     * @return el promedio cacheado; sin calificaciones cuando no hay dato
     */
    public RatingSummary executeForUserId(String userId) {
        Long courierUserId = toAuthUserId(userId);
        if (courierUserId == null) {
            return RatingSummary.none();
        }
        return execute(List.of(courierUserId)).getOrDefault(courierUserId, RatingSummary.none());
    }

    private Long toAuthUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
