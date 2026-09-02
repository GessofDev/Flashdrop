package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryRouteJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaDeliveryRouteRepository extends JpaRepository<DeliveryRouteJpaEntity, Long> {

    Optional<DeliveryRouteJpaEntity> findByOrderId(Long orderId);

    List<DeliveryRouteJpaEntity> findByDeliveryPersonId(Long deliveryPersonId);
}
