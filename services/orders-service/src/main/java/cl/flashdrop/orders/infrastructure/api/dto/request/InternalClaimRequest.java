package cl.flashdrop.orders.infrastructure.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO de entrada para el endpoint interno de claim delegado por Delivery Service
 * ({@code POST /api/internal/orders/claim}).
 *
 * <p>Contrato real, verificado contra la implementación efectiva de Delivery
 * (commit {@code be86777}, {@code HttpInternalOrdersClientAdapter} +
 * {@code HttpInternalOrdersClientAdapterTest$RequestBodyShape}): Delivery manda
 * el {@code userId} crudo del subject del JWT — NO el {@code delivery.id} ya
 * resuelto. Orders resuelve {@code userId → delivery.id} vía
 * {@code DeliveryPort.findDeliveryIdByUserId}, igual que en el endpoint público
 * legacy ({@link ClaimDeliveryRequest}).</p>
 *
 * <p>Long extremo a extremo en el wire (D3): sin UUID cruzando el límite entre
 * servicios.</p>
 */
public record InternalClaimRequest(

        @NotNull(message = "El userId es obligatorio")
        Long userId,

        @NotEmpty(message = "Debe indicar al menos un pedido")
        List<@NotNull(message = "orderIds no puede contener valores nulos") Long> orderIds

) {
}
