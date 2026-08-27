package cl.flashdrop.orders.infrastructure.adapter.outbound.http.dto;

import java.util.UUID;

/**
 * DTO de transporte interno de Auth para la persona que recoge.
 */
public record InternalDeliveryPersonDto(
        Long id,
        String fullName,
        String phone
) {
}
