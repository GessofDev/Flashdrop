package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ReleaseAssignedOrderUseCase;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.api.dto.request.ReleaseOrderRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderReleaseController {

    private final ReleaseAssignedOrderUseCase releaseUseCase;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/{orderId}/release")
    public ResponseEntity<ApiResponse<Void>> release(
            @PathVariable UUID orderId,
            @Valid @RequestBody ReleaseOrderRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        UUID currentUserId = currentUserResolver.requireCurrentUserId();
        Set<Role> currentRoles = currentUserResolver.requireCurrentRoles();

        if (!currentRoles.contains(Role.REPARTIDOR)) {
            throw new ForbiddenOperationException("Solo los repartidores pueden liberar pedidos asignados");
        }

        releaseUseCase.execute(orderId, currentUserId, request.reason(), correlationId);

        return ResponseEntity.ok(ApiResponse.success("Pedido liberado exitosamente"));
    }
}
