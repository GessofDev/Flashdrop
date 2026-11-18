package com.flashdrop.delivery.application.port.outbound;

import java.util.Collection;
import java.util.Map;

import com.flashdrop.delivery.domain.valueobjects.RatingSummary;

/**
 * Cache local de los promedios de calificacion de los repartidores.
 *
 * <p>delivery-service no lee la base de reviews-service (AGENTS.md §2): guarda
 * en su propia base el ultimo agregado conocido para poder servirlo cuando el
 * pull falla.
 *
 * <p>La clave es el id del repartidor en auth (auth.users.id), que es el
 * destino que reviews-service usa para el tipo REPARTIDOR. Es el mismo valor
 * que {@code internal.delivery_persons.user_id} guarda como texto.
 */
public interface DeliveryPersonRatingStatsRepository {

    /**
     * Promedios cacheados de los repartidores indicados. Los que no tienen
     * fila no aparecen en el mapa: no hay dato conocido para ellos.
     */
    Map<Long, RatingSummary> findByCourierUserIds(Collection<Long> courierUserIds);

    /**
     * Reemplaza el dato cacheado de un lote de repartidores por el agregado
     * recibido. Los repartidores del lote que no vienen en el agregado quedan
     * sin cache (no tienen calificaciones).
     *
     * @param courierUserIds ids de auth de los repartidores del lote
     * @param aggregates     agregado recibido, indexado por id de repartidor
     */
    void replaceBatch(Collection<Long> courierUserIds, Map<Long, RatingSummary> aggregates);
}
