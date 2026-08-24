package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.valueobjects.VehicleType;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryPersonJpaEntity;

import java.time.Instant;
import java.util.Optional;

public class JpaDeliveryPersonRepositoryAdapter implements DeliveryPersonRepository {

    private final JpaDeliveryPersonRepository jpaRepository;

    public JpaDeliveryPersonRepositoryAdapter(JpaDeliveryPersonRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<DeliveryPerson> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<DeliveryPerson> findByUserId(String userId) {
        return jpaRepository.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public boolean existsByUserId(String userId) {
        return jpaRepository.findByUserId(userId).isPresent();
    }

    @Override
    public DeliveryPerson save(DeliveryPerson deliveryPerson) {
        DeliveryPersonJpaEntity entity = toEntity(deliveryPerson);
        DeliveryPersonJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    private DeliveryPerson toDomain(DeliveryPersonJpaEntity entity) {
        return new DeliveryPerson(
                entity.getId(),
                entity.getUserId(),
                null,
                entity.getCreatedAt()
        );
    }

    private DeliveryPersonJpaEntity toEntity(DeliveryPerson person) {
        return new DeliveryPersonJpaEntity(
                person.getId(),
                person.getUserId(),
                true,
                person.getCreatedAt() != null ? person.getCreatedAt() : Instant.now()
        );
    }
}
