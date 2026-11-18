package com.flashdrop.reviews.infrastructure.adapter.outbound.client.dto;

/**
 * Respuesta del endpoint interno de orders-service
 * {@code GET /api/internal/orders/{id}} (T4).
 *
 * <p>Todos los ids son <b>numéricos públicos</b>: el id público de la orden,
 * el id de usuario (auth) del cliente, el id de restaurante (catalog) y el id
 * de usuario (auth) del repartidor.</p>
 *
 * @param orderId       id numérico público de la orden.
 * @param status        estado de la orden (p. ej. {@code ENTREGADO}).
 * @param clientId      id de usuario (auth) del cliente de la orden.
 * @param restaurantId  id de restaurante (catalog) de la orden.
 * @param courierUserId id de usuario (auth) del repartidor, o {@code null}.
 */
public record InternalOrderSummaryResponse(
        Long orderId,
        String status,
        Long clientId,
        Long restaurantId,
        Long courierUserId
) {
}
