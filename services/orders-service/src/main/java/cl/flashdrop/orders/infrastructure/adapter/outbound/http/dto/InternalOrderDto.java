package cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto;

/**
 * DTO de salida del contrato interno orders C-8.
 *
 * <p>Expondido por orders-service a sus consumidores internos. Usa {@code Long} como
 * identificador externo (convención UUID↔Long); la conversión al dominio ({@code UUID})
 * la realiza {@code IdConverter} dentro del adapter/repositorio.</p>
 *
 * <p>Campos y nombres según el contrato C-8 definido en {@code MIGRATION_PLAN.md}
 * (sección 8.3): {@code id, clientId, restaurantId, deliveryId, status, address}.
 * {@code total} no forma parte del contrato y no se expone aquí.</p>
 */
public record InternalOrderDto(
        Long id,
        Long clientId,
        Long restaurantId,
        Long deliveryId,
        String status,
        String address
) {
}
