package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.model.DeliveryOperationState;
import cl.flashdrop.orders.domain.model.DeliveryOperationType;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.DeliveryOperationRepositoryPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseAssignedOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final DeliveryPort deliveryPort;
    private final DeliveryOperationRepositoryPort deliveryOperationRepository;

    public void execute(UUID orderId, UUID userId, String reason, String correlationId) {
        UUID deliveryId = deliveryPort.findDeliveryIdByUserId(userId)
                .orElseThrow(() -> new OrderDomainException("El usuario no tiene perfil de repartidor"));

        DeliveryOperation operation = prepareRelease(orderId, userId, deliveryId, reason, correlationId);
        if (operation == null) {
            return;
        }

        try {
            deliveryPort.releaseRoute(orderId, deliveryId);
        } catch (ExternalServiceException e) {
            if (e.getStatus() != null && e.getStatus().is4xxClientError()) {
                finalizeFailed(operation.id(), e.getStatus().name());
            }
            throw e;
        }

        finalizeSuccess(orderId, deliveryId, operation.id());
    }

    @Transactional
    public DeliveryOperation prepareRelease(UUID orderId, UUID userId, UUID deliveryId, String reason, String correlationId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));

        if (order.getStatus() != OrderStatus.LISTO_PARA_RETIRO) {
            throw new OrderDomainException("Solo se pueden liberar pedidos en estado Listo para retiro");
        }

        if (order.getDeliveryId() == null) {
            List<DeliveryOperation> ops = deliveryOperationRepository.findByOrderId(orderId);
            boolean alreadyReleasedByCourier = ops.stream()
                    .anyMatch(op -> op.operationType() == DeliveryOperationType.RELEASE
                            && op.state() == DeliveryOperationState.SUCCEEDED
                            && deliveryId.equals(op.previousDeliveryId()));
            if (alreadyReleasedByCourier) {
                log.info("Pedido {} ya fue liberado previamente por repartidor {}. Idempotencia.", orderId, deliveryId);
                return null;
            }
            throw new OrderDomainException("El pedido no tiene repartidor asignado");
        }

        if (!deliveryId.equals(order.getDeliveryId())) {
            throw new ForbiddenOperationException("No puedes liberar un pedido asignado a otro repartidor");
        }

        DeliveryOperation pending = DeliveryOperation.pending(
                orderId,
                DeliveryOperationType.RELEASE,
                userId,
                Role.REPARTIDOR,
                reason,
                null,
                deliveryId,
                order.getStatus(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                OffsetDateTime.now()
        );

        return deliveryOperationRepository.create(pending);
    }

    @Transactional
    public void finalizeSuccess(UUID orderId, UUID expectedDeliveryId, Long operationId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));

        if (expectedDeliveryId.equals(order.getDeliveryId())) {
            order.setDeliveryId(null);
            orderRepository.save(order);
        }

        DeliveryOperation op = deliveryOperationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalStateException("Operacion no encontrada: " + operationId));

        DeliveryOperation succeeded = op.succeed(null, order.getStatus(), OffsetDateTime.now());
        deliveryOperationRepository.save(succeeded);
        log.info("Liberacion completada para pedido {} y operacion {}", orderId, operationId);
    }

    @Transactional
    public void finalizeFailed(Long operationId, String failureCode) {
        DeliveryOperation op = deliveryOperationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalStateException("Operacion no encontrada: " + operationId));

        DeliveryOperation failed = op.fail(failureCode, OffsetDateTime.now());
        deliveryOperationRepository.save(failed);
        log.warn("Operacion de liberacion {} marcada como FAILED con codigo {}", operationId, failureCode);
    }
}
