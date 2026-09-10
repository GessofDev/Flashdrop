package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryPersonJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaDeliveryPersonRepository extends JpaRepository<DeliveryPersonJpaEntity, Long> {

    Optional<DeliveryPersonJpaEntity> findByUserId(String userId);
}
