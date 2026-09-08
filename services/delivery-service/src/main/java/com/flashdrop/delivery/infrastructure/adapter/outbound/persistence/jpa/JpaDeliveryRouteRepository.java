package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryRouteJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaDeliveryRouteRepository extends JpaRepository<DeliveryRouteJpaEntity, Long> {

    Optional<DeliveryRouteJpaEntity> findByOrderId(Long orderId);

    List<DeliveryRouteJpaEntity> findByDeliveryPersonId(Long deliveryPersonId);

    /**
     * Plan §9.5 D8 — concurrency primitive for the claim flow. The
     * {@code PESSIMISTIC_WRITE} lock causes Hibernate to issue
     * {@code SELECT … FOR UPDATE} against PostgreSQL, which prevents two
     * concurrent claim transactions from both reading the same row with
     * {@code delivery_person_id = NULL} and both winning the update.
     *
     * <p>The UNIQUE constraint added in V5 is the safety net for the rare
     * case where two requests sneak past the lock (e.g. two separate JPA
     * persistence contexts operating on different transactions).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM DeliveryRouteJpaEntity r WHERE r.orderId = :orderId")
    Optional<DeliveryRouteJpaEntity> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
