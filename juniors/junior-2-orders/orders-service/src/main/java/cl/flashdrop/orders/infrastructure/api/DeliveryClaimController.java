package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ClaimDeliveryOrdersUseCase;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.api.dto.request.InternalClaimRequest;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controlador interno de Orders para el flujo de claim delegado por Delivery Service.
 *
 * <p>Expone {@code POST /api/internal/orders/claim}, protegido automáticamente por
 * {@link cl.flashdrop.orders.config.InternalApiKeyFilter} (cubre todo el prefijo
 * {@code /api/internal/}; no requiere configuración adicional).</p>
 *
 * <p>Recibe el {@code userId} crudo del repartidor (subject del JWT que Delivery ya
 * validó) y reutiliza el flujo canónico existente ({@link ClaimDeliveryOrdersUseCase#execute})
 * para resolverlo a {@code delivery.id} vía {@code findDeliveryIdByUserId} — el mismo
 * mecanismo que ya usa {@link DeliveryController} (endpoint público legacy
 * {@code POST /api/delivery/claim}, sin cambios). La única diferencia entre ambos
 * controllers es de dónde sale el {@code userId}: acá del body (Delivery ya autenticó
 * al repartidor de su lado); en el legacy, del propio body sin autenticar (ver ORD-F2,
 * fuera de alcance de este cambio).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/orders")
@RequiredArgsConstructor
public class DeliveryClaimController {

    private final ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    @PostMapping("/claim")
    public ApiResponse<Void> claim(@Valid @RequestBody InternalClaimRequest request) {
        log.debug("POST /api/internal/orders/claim userId={} orderIds={}",
                request.userId(), request.orderIds());

        UUID userId = IdConverter.toUuid(request.userId());
        List<UUID> orderIds = request.orderIds().stream()
                .map(IdConverter::toUuid)
                .collect(Collectors.toList());

        claimDeliveryOrdersUseCase.execute(userId, orderIds);
        return ApiResponse.success("Pedido reclamado");
    }
}
