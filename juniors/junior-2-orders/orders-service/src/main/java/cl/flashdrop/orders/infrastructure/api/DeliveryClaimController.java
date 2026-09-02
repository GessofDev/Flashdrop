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
 * Controlador interno de Orders para el flujo de claim delegado por Delivery Service
 * (decisiones D5/D6 de "Plan_ Servicio_delivery.txt").
 *
 * <p>Expone {@code POST /api/internal/orders/claim}, protegido automáticamente por
 * {@link cl.flashdrop.orders.config.InternalApiKeyFilter} (cubre todo el prefijo
 * {@code /api/internal/}; no requiere configuración adicional).</p>
 *
 * <p>A diferencia de {@link DeliveryController} (endpoint público legacy
 * {@code POST /api/delivery/claim}, que sigue existiendo sin cambios), aquí
 * {@code deliveryPersonId} llega ya resuelto por Delivery Service — se usa
 * directamente, sin volver a resolverlo vía {@code findDeliveryIdByUserId}
 * (decisión D5, cerrada).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/orders")
@RequiredArgsConstructor
public class DeliveryClaimController {

    private final ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    @PostMapping("/claim")
    public ApiResponse<Void> claim(@Valid @RequestBody InternalClaimRequest request) {
        log.debug("POST /api/internal/orders/claim deliveryPersonId={} orderIds={}",
                request.deliveryPersonId(), request.orderIds());

        UUID deliveryId = IdConverter.toUuid(request.deliveryPersonId());
        List<UUID> orderIds = request.orderIds().stream()
                .map(IdConverter::toUuid)
                .collect(Collectors.toList());

        claimDeliveryOrdersUseCase.executeForResolvedDelivery(deliveryId, orderIds);
        return ApiResponse.success("Pedido reclamado");
    }
}
