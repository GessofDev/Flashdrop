package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.CancelPickedUpOrderUseCase;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.infrastructure.api.dto.request.CancelAfterPickupRequest;
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
public class OrderCancellationController {

    private final CancelPickedUpOrderUseCase cancelPickedUpOrderUseCase;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/{orderId}/cancel-after-pickup")
    public ResponseEntity<ApiResponse<Void>> cancelAfterPickup(
            @PathVariable("orderId") String rawOrderId,
            @Valid @RequestBody CancelAfterPickupRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        // F17: la app del repartidor tiene el id numérico en pantalla (lo que emite
        // Delivery en RouteResponse.orderId), no el UUID interno. Se aceptan ambos.
        UUID orderId = OrderIdResolver.resolve(rawOrderId);

        UUID currentUserId = currentUserResolver.requireCurrentUserId();
        Set<Role> currentRoles = currentUserResolver.requireCurrentRoles();

        boolean isSupport = currentRoles.contains(Role.SOPORTE);
        boolean isCourier = currentRoles.contains(Role.REPARTIDOR);

        if (!isSupport && !isCourier) {
            throw new ForbiddenOperationException("Solo Soporte o el Repartidor asignado pueden anular pedidos retirados");
        }

        // Support authorization prevails when both roles are present
        Role effectiveRole = isSupport ? Role.SOPORTE : Role.REPARTIDOR;
        OrderStatus targetStatus = OrderStatus.fromValue(request.status());

        cancelPickedUpOrderUseCase.execute(
                orderId,
                currentUserId,
                effectiveRole,
                targetStatus,
                request.reason(),
                correlationId
        );

        return ResponseEntity.ok(ApiResponse.success("Pedido anulado exitosamente tras retiro"));
    }
}
