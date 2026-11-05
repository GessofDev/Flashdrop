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
public class CancelPickedUpOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final DeliveryPort deliveryPort;
    private final DeliveryOperationRepositoryPort deliveryOperationRepository;

    public void execute(UUID orderId, UUID userId, Role role, OrderStatus targetStatus, String reason, String correlationId) {
        if (targetStatus != OrderStatus.CANCELADO && targetStatus != OrderStatus.RECHAZADO) {
            throw new OrderDomainException("El estado final debe ser Cancelado o Rechazado");
        }

        UUID courierDeliveryId = null;
        if (role == Role.REPARTIDOR) {
            courierDeliveryId = deliveryPort.findDeliveryIdByUserId(userId)
                    .orElseThrow(() -> new OrderDomainException("El usuario no tiene perfil de repartidor"));
        }

        DeliveryOperation operation = prepareCancellation(orderId, userId, role, courierDeliveryId, targetStatus, reason, correlationId);
        if (operation == null) {
            return;
        }

        try {
            // Delivery accepts status tokens "Cancelado" or "Rechazado" (RouteStatus enum handles case-insensitive / db values)
            deliveryPort.closeRoute(orderId, targetStatus.getValue());
        } catch (ExternalServiceException e) {
            if (e.getStatus() != null && e.getStatus().is4xxClientError()) {
                finalizeFailed(operation.id(), e.getStatus().name());
            }
            throw e;
        }

        finalizeSuccess(orderId, targetStatus, operation.id());
    }

    @Transactional
    public DeliveryOperation prepareCancellation(
            UUID orderId,
            UUID userId,
            Role role,
            UUID courierDeliveryId,
            OrderStatus targetStatus,
            String reason,
            String correlationId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));

        // Idempotency check: if order is already in the terminal state requested
        if (order.getStatus() == targetStatus) {
            List<DeliveryOperation> ops = deliveryOperationRepository.findByOrderId(orderId);
            boolean alreadyCancelled = ops.stream()
                    .anyMatch(op -> op.operationType() == DeliveryOperationType.CANCEL_AFTER_PICKUP
                            && op.state() == DeliveryOperationState.SUCCEEDED
                            && targetStatus.equals(op.resultingStatus()));
            if (alreadyCancelled) {
                log.info("Pedido {} ya fue anulado previamente a estado {}. Idempotencia.", orderId, targetStatus);
                return null;
            }
        }

        // Terminal conflicting check
        if (order.getStatus() == OrderStatus.CANCELADO || order.getStatus() == OrderStatus.RECHAZADO || order.getStatus() == OrderStatus.ENTREGADO) {
            throw new OrderDomainException("El pedido ya está en estado terminal " + order.getStatus().getValue());
        }

        // Source status validation: only RETIRADO or legacy EN_CAMINO allowed
        if (order.getStatus() != OrderStatus.RETIRADO && order.getStatus() != OrderStatus.EN_CAMINO) {
            throw new OrderDomainException("Solo se pueden anular pedidos retirados (estado actual: " + order.getStatus().getValue() + ")");
        }

        // Ownership authorization: if Repartidor, must own the order
        if (role == Role.REPARTIDOR) {
            if (order.getDeliveryId() == null || !order.getDeliveryId().equals(courierDeliveryId)) {
                throw new ForbiddenOperationException("No puedes anular un pedido asignado a otro repartidor");
            }
        }

        DeliveryOperation pending = DeliveryOperation.pending(
                orderId,
                DeliveryOperationType.CANCEL_AFTER_PICKUP,
                userId,
                role,
                reason,
                targetStatus,
                order.getDeliveryId(),
                order.getStatus(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                OffsetDateTime.now()
        );

        return deliveryOperationRepository.create(pending);
    }

    @Transactional
    public void finalizeSuccess(UUID orderId, OrderStatus targetStatus, Long operationId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));

        order.setStatus(targetStatus);
        // Note: as defined in design, delivery_id is preserved for audit traceability
        orderRepository.save(order);

        DeliveryOperation op = deliveryOperationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalStateException("Operacion no encontrada: " + operationId));

        DeliveryOperation succeeded = op.succeed(order.getDeliveryId(), targetStatus, OffsetDateTime.now());
        deliveryOperationRepository.save(succeeded);
        log.info("Anulación completada para pedido {} con estado {} y operacion {}", orderId, targetStatus, operationId);
    }

    @Transactional
    public void finalizeFailed(Long operationId, String failureCode) {
        DeliveryOperation op = deliveryOperationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new IllegalStateException("Operacion no encontrada: " + operationId));

        DeliveryOperation failed = op.fail(failureCode, OffsetDateTime.now());
        deliveryOperationRepository.save(failed);
        log.warn("Operacion de anulacion {} marcada como FAILED con codigo {}", operationId, failureCode);
    }
}
