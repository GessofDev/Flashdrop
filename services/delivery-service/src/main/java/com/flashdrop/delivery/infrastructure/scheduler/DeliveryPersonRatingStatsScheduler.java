package com.flashdrop.delivery.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.flashdrop.delivery.application.usecase.RefreshDeliveryPersonRatingStatsUseCase;

/**
 * Pull programado de los promedios de calificacion de los repartidores.
 *
 * <p>Via unica de sincronizacion: cada TTL (~5 min) se pide el agregado de
 * todos los repartidores a reviews-service y se deja cacheado en la base de
 * delivery. Los lectores nunca llaman a reviews-service, asi que el perfil
 * del repartidor no depende de que ese servicio este arriba.
 *
 * <p>El primer pull ocurre un TTL despues del arranque: hasta entonces el
 * repartidor aparece sin calificaciones, que es el estado correcto de una
 * cache recien creada.
 */
@Component
public class DeliveryPersonRatingStatsScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryPersonRatingStatsScheduler.class);

    private final RefreshDeliveryPersonRatingStatsUseCase refreshDeliveryPersonRatingStatsUseCase;

    public DeliveryPersonRatingStatsScheduler(
            RefreshDeliveryPersonRatingStatsUseCase refreshDeliveryPersonRatingStatsUseCase
    ) {
        this.refreshDeliveryPersonRatingStatsUseCase = refreshDeliveryPersonRatingStatsUseCase;
    }

    @Scheduled(
            initialDelayString = "${delivery.reviews.ttl:PT5M}",
            fixedDelayString = "${delivery.reviews.ttl:PT5M}"
    )
    public void refreshRatingStats() {
        RefreshDeliveryPersonRatingStatsUseCase.RefreshResult result =
                refreshDeliveryPersonRatingStatsUseCase.execute();
        if (result.failed() > 0) {
            log.warn("Pull de promedios de calificacion con {} lotes fallidos; se conserva el valor cacheado",
                    result.failed());
        }
        log.info("Pull de promedios de calificacion terminado: {} lotes actualizados, {} lotes con error",
                result.refreshed(), result.failed());
    }
}
