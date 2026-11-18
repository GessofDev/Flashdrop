package com.flashdrop.reviews.infrastructure.adapter.outbound.client.dto;

/**
 * Respuesta del endpoint interno de catalog-service
 * {@code GET /api/internal/restaurants?userId={userId}}.
 *
 * <p>Solo se usa el {@code id} del restaurante (para validar la participación
 * del rol {@code Restaurante}); el resto de los campos no se consumen.</p>
 *
 * @param id      id del restaurante (catalog).
 * @param userId  id de usuario (auth) del dueño de la tienda.
 * @param name    nombre de la tienda.
 * @param address dirección de la tienda.
 * @param open    si la tienda está abierta.
 */
public record InternalRestaurantResponse(
        Long id,
        Long userId,
        String name,
        String address,
        boolean open
) {
}
