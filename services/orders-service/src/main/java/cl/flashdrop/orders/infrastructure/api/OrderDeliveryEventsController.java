package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.GetDeliveryEventsUseCase;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.model.DeliveryOperation;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import cl.flashdrop.orders.infrastructure.api.dto.response.DeliveryOperationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderDeliveryEventsController {

    private final GetDeliveryEventsUseCase getDeliveryEventsUseCase;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/{orderId}/delivery-events")
    public ResponseEntity<ApiResponse<List<DeliveryOperationResponse>>> getDeliveryEvents(
            @PathVariable UUID orderId) {

        currentUserResolver.requireCurrentUserId();
        Set<Role> currentRoles = currentUserResolver.requireCurrentRoles();

        if (!currentRoles.contains(Role.SOPORTE)) {
            throw new ForbiddenOperationException("Solo Soporte tiene acceso a la bitácora de eventos de entrega");
        }

        List<DeliveryOperation> events = getDeliveryEventsUseCase.execute(orderId);

        List<DeliveryOperationResponse> response = events.stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private DeliveryOperationResponse toResponse(DeliveryOperation op) {
        return new DeliveryOperationResponse(
                op.id(),
                op.orderId(),
                op.operationType().name(),
                op.state().name(),
                op.actorUserId(),
                op.actorRole().name(),
                op.reason(),
                op.targetStatus() != null ? op.targetStatus().getValue() : null,
                op.previousDeliveryId(),
                op.resultingDeliveryId(),
                op.previousStatus() != null ? op.previousStatus().getValue() : null,
                op.resultingStatus() != null ? op.resultingStatus().getValue() : null,
                op.correlationId(),
                op.createdAt(),
                op.updatedAt()
        );
    }
}
