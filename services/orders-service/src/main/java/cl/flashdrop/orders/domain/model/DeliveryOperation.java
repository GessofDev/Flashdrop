package cl.flashdrop.orders.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeliveryOperation(
        Long id,
        UUID orderId,
        DeliveryOperationType operationType,
        DeliveryOperationState state,
        UUID actorUserId,
        Role actorRole,
        String reason,
        OrderStatus targetStatus,
        UUID previousDeliveryId,
        UUID resultingDeliveryId,
        OrderStatus previousStatus,
        OrderStatus resultingStatus,
        String correlationId,
        String failureCode,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static DeliveryOperation pending(
            UUID orderId,
            DeliveryOperationType operationType,
            UUID actorUserId,
            Role actorRole,
            String reason,
            OrderStatus targetStatus,
            UUID previousDeliveryId,
            OrderStatus previousStatus,
            String correlationId,
            OffsetDateTime createdAt
    ) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo de la operacion es obligatorio");
        }
        String trimmedReason = reason.trim();
        if (trimmedReason.length() > 500) {
            throw new IllegalArgumentException("El motivo no puede exceder 500 caracteres");
        }
        if (operationType == DeliveryOperationType.CANCEL_AFTER_PICKUP) {
            if (targetStatus != OrderStatus.CANCELADO && targetStatus != OrderStatus.RECHAZADO) {
                throw new IllegalArgumentException("La anulacion posterior al retiro exige estado CANCELADO o RECHAZADO");
            }
        }
        return new DeliveryOperation(
                null,
                orderId,
                operationType,
                DeliveryOperationState.PENDING,
                actorUserId,
                actorRole,
                trimmedReason,
                targetStatus,
                previousDeliveryId,
                null,
                previousStatus,
                null,
                correlationId,
                null,
                createdAt,
                createdAt
        );
    }

    public DeliveryOperation succeed(UUID resultingDeliveryId, OrderStatus resultingStatus, OffsetDateTime updatedAt) {
        if (this.state != DeliveryOperationState.PENDING) {
            throw new IllegalStateException("La operacion ya no esta pendiente: " + this.state);
        }
        return new DeliveryOperation(
                this.id,
                this.orderId,
                this.operationType,
                DeliveryOperationState.SUCCEEDED,
                this.actorUserId,
                this.actorRole,
                this.reason,
                this.targetStatus,
                this.previousDeliveryId,
                resultingDeliveryId,
                this.previousStatus,
                resultingStatus,
                this.correlationId,
                null,
                this.createdAt,
                updatedAt
        );
    }

    public DeliveryOperation fail(String failureCode, OffsetDateTime updatedAt) {
        if (this.state != DeliveryOperationState.PENDING) {
            throw new IllegalStateException("La operacion ya no esta pendiente: " + this.state);
        }
        return new DeliveryOperation(
                this.id,
                this.orderId,
                this.operationType,
                DeliveryOperationState.FAILED,
                this.actorUserId,
                this.actorRole,
                this.reason,
                this.targetStatus,
                this.previousDeliveryId,
                this.resultingDeliveryId,
                this.previousStatus,
                null,
                this.correlationId,
                failureCode,
                this.createdAt,
                updatedAt
        );
    }

    public DeliveryOperation withId(Long id) {
        return new DeliveryOperation(
                id,
                this.orderId,
                this.operationType,
                this.state,
                this.actorUserId,
                this.actorRole,
                this.reason,
                this.targetStatus,
                this.previousDeliveryId,
                this.resultingDeliveryId,
                this.previousStatus,
                this.resultingStatus,
                this.correlationId,
                this.failureCode,
                this.createdAt,
                this.updatedAt
        );
    }
}
