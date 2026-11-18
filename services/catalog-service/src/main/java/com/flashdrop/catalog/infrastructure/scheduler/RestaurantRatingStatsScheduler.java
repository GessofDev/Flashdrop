package com.flashdrop.catalog.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.flashdrop.catalog.application.usecase.RefreshRestaurantRatingStatsUseCase;

/**
 * Pull programado de los promedios de calificacion.
 *
 * <p>Via unica de sincronizacion: cada TTL (~5 min) se pide el agregado de
 * todos los restaurantes a reviews-service y se deja cacheado en la base de
 * catalog. Los lectores nunca llaman a reviews-service, asi que el marketplace
 * no depende de que ese servicio este arriba.
 *
 * <p>El primer pull ocurre un TTL despues del arranque: hasta entonces el
 * marketplace muestra los restaurants sin calificaciones, que es el estado
 * correcto de una cache recien creada.
 */
@Component
@Profile("postgres")
public class RestaurantRatingStatsScheduler {

    private static final Logger log = LoggerFactory.getLogger(RestaurantRatingStatsScheduler.class);

    private final RefreshRestaurantRatingStatsUseCase refreshRestaurantRatingStatsUseCase;

    public RestaurantRatingStatsScheduler(RefreshRestaurantRatingStatsUseCase refreshRestaurantRatingStatsUseCase) {
        this.refreshRestaurantRatingStatsUseCase = refreshRestaurantRatingStatsUseCase;
    }

    @Scheduled(
            initialDelayString = "${catalog.reviews.ttl:PT5M}",
            fixedDelayString = "${catalog.reviews.ttl:PT5M}"
    )
    public void refreshRatingStats() {
        RefreshRestaurantRatingStatsUseCase.RefreshResult result = refreshRestaurantRatingStatsUseCase.execute();
        if (result.failed() > 0) {
            log.warn("Pull de promedios de calificacion con {} lotes fallidos; se conserva el valor cacheado",
                    result.failed());
        }
        log.info("Pull de promedios de calificacion terminado: {} lotes actualizados, {} lotes con error",
                result.refreshed(), result.failed());
    }
}
