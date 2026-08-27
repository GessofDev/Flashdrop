package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ClaimDeliveryOrdersUseCase;
import cl.flashdrop.orders.infrastructure.api.dto.request.ClaimDeliveryRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para operaciones de Delivery (rutas y reclamo de pedidos).
 *
 * <p>Expone {@code POST /api/delivery/claim} respetando el contrato definido en openapi.yaml.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    @PostMapping("/claim")
    public ApiResponse<Void> claimOrders(@Valid @RequestBody ClaimDeliveryRequest request) {
        log.debug("POST /api/delivery/claim");
        claimDeliveryOrdersUseCase.execute(request.getUserId(), request.resolvedOrderIds());
        return ApiResponse.success("Pedido reclamado");
    }
}
