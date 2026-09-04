package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.exception.RouteAlreadyAssignedException;
import com.flashdrop.delivery.domain.exception.RouteNotPrecreatedException;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity.DeliveryRouteJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class JpaRouteRepositoryAdapter implements RouteRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaRouteRepositoryAdapter.class);

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

    /**
     * Plan §9.5 D8. Selects the row with {@code PESSIMISTIC_WRITE} so two
     * concurrent claim transactions serialise on the same row, then mutates
     * in-place + flushes inside the current persistence context. Throws:
     * <ul>
     *   <li>{@link RouteNotPrecreatedException} when no row exists for the
     *       given {@code orderId} (Orders has not published the route yet);</li>
     *   <li>{@link RouteAlreadyAssignedException} when the row exists but its
     *       {@code delivery_person_id} is already set.</li>
     * </ul>
     * The transition {@code PENDIENTE → ASSIGNED} is applied on the same
     * managed entity so the lock is released only after the UPDATE flushes
     * to the database.
     */
    @Override
    public DeliveryRoute assignDeliveryPerson(Long orderId, Long deliveryPersonId) {
        DeliveryRouteJpaEntity entity = jpaRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new RouteNotPrecreatedException(orderId));

        if (entity.getDeliveryPersonId() != null) {
            log.warn("Claim collision: orderId={} already assigned to deliveryPersonId={}; "
                            + "rejecting claim for deliveryPersonId={}",
                    orderId, entity.getDeliveryPersonId(), deliveryPersonId);
            throw new RouteAlreadyAssignedException(orderId);
        }

        entity.setDeliveryPersonId(deliveryPersonId);
        entity.setStatus(RouteStatus.ASSIGNED.getDbValue());
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
