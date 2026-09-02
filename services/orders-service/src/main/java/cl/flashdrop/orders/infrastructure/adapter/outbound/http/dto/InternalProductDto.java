package cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de transporte interno de Catalog para products.
 *
 * <p>La entidad expuesta a Orders usa {@code Long} como id interno; el adapter
 * convierte a {@code UUID} del dominio Orders.</p>
 */
public record InternalProductDto(
        Long id,
        Long restaurantId,
        String name,
        String description,
        String image,
        BigDecimal price,
        Boolean available
) {
}
