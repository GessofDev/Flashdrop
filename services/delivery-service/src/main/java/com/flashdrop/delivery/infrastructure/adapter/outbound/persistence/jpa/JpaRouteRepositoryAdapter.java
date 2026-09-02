package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryRouteJpaEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JpaRouteRepositoryAdapter implements RouteRepository {

    private final JpaDeliveryRouteRepository jpaRepository;

    public JpaRouteRepositoryAdapter(JpaDeliveryRouteRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<DeliveryRoute> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<DeliveryRoute> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId).map(this::toDomain);
    }

    @Override
    public List<DeliveryRoute> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<DeliveryRoute> findByDeliveryPersonId(Long deliveryPersonId) {
        return jpaRepository.findByDeliveryPersonId(deliveryPersonId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public DeliveryRoute save(DeliveryRoute route) {
        DeliveryRouteJpaEntity entity = toEntity(route);
        DeliveryRouteJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public boolean existsByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId).isPresent();
    }

    @Override
    public DeliveryRoute updateStatus(Long id, String status) {
        Optional<DeliveryRouteJpaEntity> optEntity = jpaRepository.findById(id);
        if (optEntity.isEmpty()) {
            return null;
        }
        DeliveryRouteJpaEntity entity = optEntity.get();
        entity.setStatus(status);
        entity.setUpdatedAt(Instant.now());
        DeliveryRouteJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    private DeliveryRoute toDomain(DeliveryRouteJpaEntity entity) {
        return new DeliveryRoute(
                entity.getId(),
                entity.getOrderId(),
                entity.getDeliveryPersonId(),
                entity.getPickupAddress(),
                entity.getDeliveryAddress(),
                entity.getDistanceKm() != null ? Distance.of(entity.getDistanceKm()) : Distance.zero(),
                entity.getEstimatedMinutes() != null ? EstimatedTime.of(entity.getEstimatedMinutes()) : EstimatedTime.zero(),
                RouteStatus.fromDbValue(entity.getStatus()),
                entity.getCreatedAt()
        );
    }

    private DeliveryRouteJpaEntity toEntity(DeliveryRoute route) {
        return new DeliveryRouteJpaEntity(
                route.getId(),
                route.getOrderId(),
                route.getDeliveryPersonId(),
                route.getPickupAddress(),
                route.getDeliveryAddress(),
                route.getDistanceKm() != null ? route.getDistanceKm().value() : null,
                route.getEstimatedMinutes() != null ? route.getEstimatedMinutes().minutes() : null,
                route.getStatus() != null ? route.getStatus().getDbValue() : null,
                route.getCreatedAt() != null ? route.getCreatedAt() : Instant.now()
        );
    }
}
