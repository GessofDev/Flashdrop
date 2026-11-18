package com.flashdrop.catalog.application.port.outbound;

import java.util.Collection;
import java.util.Map;

import com.flashdrop.catalog.domain.model.RatingSummary;

/**
 * Cache local de los promedios de calificacion de los restaurantes.
 *
 * <p>catalog-service no lee la base de reviews-service (AGENTS.md §2): guarda
 * en su propia base el ultimo agregado conocido para poder servirlo cuando el
 * pull falla.
 */
public interface RestaurantRatingStatsRepositoryPort {

    /**
     * Promedios cacheados de los restaurantes indicados. Los que no tienen
     * fila no aparecen en el mapa: no hay dato conocido para ellos.
     */
    Map<Long, RatingSummary> findByRestaurantIds(Collection<Long> restaurantIds);

    /**
     * Reemplaza el dato cacheado de un lote de restaurantes por el agregado
     * recibido. Los restaurantes del lote que no vienen en el agregado quedan
     * sin cache (no tienen calificaciones).
     *
     * @param restaurantIds ids del lote completo
     * @param aggregates   agregado recibido, indexado por id de restaurante
     */
    void replaceBatch(Collection<Long> restaurantIds, Map<Long, RatingSummary> aggregates);
}
