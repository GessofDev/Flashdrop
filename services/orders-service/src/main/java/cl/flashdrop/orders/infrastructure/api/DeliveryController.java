package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ClaimDeliveryOrdersUseCase;
import cl.flashdrop.orders.infrastructure.api.dto.request.ClaimDeliveryRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controlador REST para operaciones de Delivery (rutas y reclamo de pedidos).
 *
 * <p>Expone {@code POST /api/delivery/claim} respetando el contrato definido en openapi.yaml.</p>
 *
 * <p><b>GAP-03 (auditoría 2026-09-04):</b> este endpoint aceptaba {@code deliveryPersonId}
 * directo del body sin ninguna autenticación (IDOR — cualquiera podía reclamar pedidos a
 * nombre de cualquier repartidor). Ahora requiere JWT ({@code SecurityConfig}) y la
 * identidad del repartidor se resuelve SIEMPRE desde el token autenticado
 * ({@link CurrentUserResolver}), nunca del body. {@code ClaimDeliveryRequest.deliveryPersonId}
 * se conserva en el DTO sólo por compatibilidad de forma del JSON con clientes existentes
 * (y para no romper el formato legacy con {@code order_id}/{@code order_ids}), pero su valor
 * ya no se usa para decidir quién reclama.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/claim")
    public ApiResponse<Void> claimOrders(@Valid @RequestBody ClaimDeliveryRequest request) {
        UUID userId = currentUserResolver.requireCurrentUserId();
        log.debug("POST /api/delivery/claim, userId autenticado={}", userId);
        claimDeliveryOrdersUseCase.execute(userId, request.resolvedOrderIds());
        return ApiResponse.success("Pedido reclamado");
    }
}
