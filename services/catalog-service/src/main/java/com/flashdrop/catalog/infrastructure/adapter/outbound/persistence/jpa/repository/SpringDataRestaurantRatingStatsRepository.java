package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.repository;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity.RestaurantRatingStatsEntity;

@Repository
public interface SpringDataRestaurantRatingStatsRepository
        extends JpaRepository<RestaurantRatingStatsEntity, Long> {

    /**
     * Escribe el agregado de un restaurante. El upsert evita el select+update
     * por fila de un merge y deja la fila lista para el proximo pull.
     */
    @Modifying
    @Query(value = """
            insert into restaurant_rating_stats (restaurant_id, average, count, updated_at)
            values (:restaurantId, :average, :count, now())
            on conflict (restaurant_id) do update
               set average = excluded.average,
                   count = excluded.count,
                   updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsert(@Param("restaurantId") Long restaurantId,
                @Param("average") Double average,
                @Param("count") Long count);

    /** Borra la cache de los restaurantes que ya no tienen calificaciones. */
    @Modifying
    @Query(value = "delete from restaurant_rating_stats where restaurant_id in (:restaurantIds)", nativeQuery = true)
    void deleteByRestaurantIds(@Param("restaurantIds") Collection<Long> restaurantIds);
}
