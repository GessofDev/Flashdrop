package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryPersonRatingStatsJpaEntity;

@Repository
public interface JpaDeliveryPersonRatingStatsRepository
        extends JpaRepository<DeliveryPersonRatingStatsJpaEntity, Long> {

    /**
     * Escribe el agregado de un repartidor. El upsert evita el select+update
     * por fila de un merge y deja la fila lista para el proximo pull.
     */
    @Modifying
    @Query(value = """
            insert into internal.delivery_person_rating_stats (courier_user_id, average, count, updated_at)
            values (:courierUserId, :average, :count, now())
            on conflict (courier_user_id) do update
               set average = excluded.average,
                   count = excluded.count,
                   updated_at = excluded.updated_at
            """, nativeQuery = true)
    void upsert(@Param("courierUserId") Long courierUserId,
                @Param("average") Double average,
                @Param("count") Long count);

    /** Borra la cache de los repartidores que ya no tienen calificaciones. */
    @Modifying
    @Query(value = "delete from internal.delivery_person_rating_stats where courier_user_id in (:courierUserIds)",
            nativeQuery = true)
    void deleteByCourierUserIds(@Param("courierUserIds") Collection<Long> courierUserIds);
}
