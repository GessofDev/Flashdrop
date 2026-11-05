package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.model.DeliveryOperationState;
import cl.flashdrop.orders.domain.model.DeliveryOperationType;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.DeliveryOperationRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderDeliveryOperationEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataDeliveryOperationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@Profile({"postgres", "default"})
public class JpaDeliveryOperationRepositoryAdapter implements DeliveryOperationRepositoryPort {

    private final SpringDataDeliveryOperationRepository repository;

    public JpaDeliveryOperationRepositoryAdapter(SpringDataDeliveryOperationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DeliveryOperation create(DeliveryOperation operation) {
        OrderDeliveryOperationEntity entity = toEntity(operation);
        OrderDeliveryOperationEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public Optional<DeliveryOperation> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOperation> findByOrderId(UUID orderId) {
        Long rawOrderId = IdConverter.toLong(orderId);
        return repository.findByOrderIdOrderByCreatedAtAsc(rawOrderId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public DeliveryOperation save(DeliveryOperation operation) {
        OrderDeliveryOperationEntity entity;
        if (operation.id() != null) {
            entity = repository.findById(operation.id())
                    .orElseGet(() -> toEntity(operation));
        } else {
            entity = toEntity(operation);
        }

        entity.setState(operation.state().name());
        entity.setResultingDeliveryId(operation.resultingDeliveryId() != null
                ? IdConverter.toLong(operation.resultingDeliveryId())
                : null);
        entity.setResultingStatus(operation.resultingStatus() != null
                ? operation.resultingStatus().getValue()
                : null);
        entity.setFailureCode(operation.failureCode());
        entity.setUpdatedAt(operation.updatedAt());

        OrderDeliveryOperationEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOperation> findByState(DeliveryOperationState state, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return repository.findByStateOrderByCreatedAtAsc(state, pageable).stream()
                .map(this::toDomain)
                .toList();
    }

    private OrderDeliveryOperationEntity toEntity(DeliveryOperation operation) {
        return OrderDeliveryOperationEntity.builder()
                .id(operation.id())
                .orderId(IdConverter.toLong(operation.orderId()))
                .operationType(operation.operationType().name())
                .state(operation.state().name())
                .actorUserId(IdConverter.toLong(operation.actorUserId()))
                .actorRole(operation.actorRole().name())
                .reason(operation.reason())
                .targetStatus(operation.targetStatus() != null ? operation.targetStatus().getValue() : null)
                .previousDeliveryId(operation.previousDeliveryId() != null
                        ? IdConverter.toLong(operation.previousDeliveryId())
                        : null)
                .resultingDeliveryId(operation.resultingDeliveryId() != null
                        ? IdConverter.toLong(operation.resultingDeliveryId())
                        : null)
                .previousStatus(operation.previousStatus() != null
                        ? operation.previousStatus().getValue()
                        : null)
                .resultingStatus(operation.resultingStatus() != null
                        ? operation.resultingStatus().getValue()
                        : null)
                .correlationId(operation.correlationId())
                .failureCode(operation.failureCode())
                .createdAt(operation.createdAt())
                .updatedAt(operation.updatedAt())
                .build();
    }

    private DeliveryOperation toDomain(OrderDeliveryOperationEntity entity) {
        return new DeliveryOperation(
                entity.getId(),
                IdConverter.toUuid(entity.getOrderId()),
                DeliveryOperationType.valueOf(entity.getOperationType()),
                DeliveryOperationState.valueOf(entity.getState()),
                IdConverter.toUuid(entity.getActorUserId()),
                Role.valueOf(entity.getActorRole()),
                entity.getReason(),
                entity.getTargetStatus() != null ? OrderStatus.fromValue(entity.getTargetStatus()) : null,
                entity.getPreviousDeliveryId() != null ? IdConverter.toUuid(entity.getPreviousDeliveryId()) : null,
                entity.getResultingDeliveryId() != null ? IdConverter.toUuid(entity.getResultingDeliveryId()) : null,
                entity.getPreviousStatus() != null ? OrderStatus.fromValue(entity.getPreviousStatus()) : null,
                entity.getResultingStatus() != null ? OrderStatus.fromValue(entity.getResultingStatus()) : null,
                entity.getCorrelationId(),
                entity.getFailureCode(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
