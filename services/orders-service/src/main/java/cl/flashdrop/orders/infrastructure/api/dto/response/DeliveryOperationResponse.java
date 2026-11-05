package cl.flashdrop.orders.infrastructure.api.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeliveryOperationResponse(
        Long id,
        UUID orderId,
        String operationType,
        String state,
        UUID actorUserId,
        String actorRole,
        String reason,
        String targetStatus,
        UUID previousDeliveryId,
        UUID resultingDeliveryId,
        String previousStatus,
        String resultingStatus,
        String correlationId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
