package cl.flashdrop.orders.infrastructure.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO de entrada para el endpoint interno de claim delegado por Delivery Service
 * (decisiones D5/D6 de "Plan_ Servicio_delivery.txt", endpoint {@code POST /api/internal/orders/claim}).
 *
 * <p>A diferencia de {@link ClaimDeliveryRequest} (endpoint público legacy
 * {@code POST /api/delivery/claim}), aquí {@code deliveryPersonId} llega YA RESUELTO
 * por Delivery Service: es directamente el {@code delivery.id} (Long externo), no un
 * userId de Auth a resolver. Orders no vuelve a resolverlo (decisión D5, cerrada:
 * no se llama a {@code findDeliveryIdByUserId} para este flujo).</p>
 *
 * <p>Contrato de tipos por decisión D3 (cerrada): Long extremo a extremo en el wire,
 * sin UUID cruzando el límite entre servicios.</p>
 */
public record InternalClaimRequest(

        @NotNull(message = "El deliveryPersonId es obligatorio")
        Long deliveryPersonId,

        @NotEmpty(message = "Debe indicar al menos un pedido")
        List<@NotNull(message = "orderIds no puede contener valores nulos") Long> orderIds

) {
}
