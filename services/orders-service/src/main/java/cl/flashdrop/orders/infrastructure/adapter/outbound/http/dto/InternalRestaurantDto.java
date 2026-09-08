package cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto;

import java.util.UUID;

/**
 * DTO de transporte interno de Catalog para restaurants.
 */
public record InternalRestaurantDto(
        Long id,
        String name,
        String address,
        Long userId
) {
}
