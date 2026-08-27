package cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto;

import java.math.BigDecimal;

/**
 * DTO de salida del contrato interno orders C-8.
 *
 * <p>Expondido por orders-service a sus consumidores internos. Usa {@code Long} como
 * identificador externo (convención UUID↔Long); la conversión al dominio ({@code UUID})
 * la realiza {@code IdConverter} dentro del adapter/repositorio.</p>
 */
public record InternalOrderDto(
        Long id,
        String status,
        Long clientId,
        Long restaurantId,
        BigDecimal total
) {
}
