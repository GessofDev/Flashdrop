package com.flashdrop.reviews.domain.model;

import com.flashdrop.reviews.domain.valueobjects.RatingTarget;

/**
 * Vista de la orden que reviews-service recibe de orders-service por el
 * endpoint interno {@code GET /api/internal/orders/{id}} (T4).
 *
 * <p>Es la <b>única</b> fuente de verdad para el estado de la orden y para
 * derivar el destino de la calificación (S7): el servidor nunca acepta el id
 * del destino desde el body de la petición.</p>
 *
 * <p><b>Espacio de ids.</b> Los tres identificadores de participantes viajan en
 * el espacio de ids <b>público</b> (el {@code Long} de auth/catalog), que es el
 * único espacio en el que se puede comparar contra el {@code sub} del JWT:</p>
 * <ul>
 *   <li>{@code clientId} → {@code auth.users.id} del cliente de la orden.</li>
 *   <li>{@code courierUserId} → {@code auth.users.id} del repartidor
 *       ({@code null} si la orden no tiene repartidor asignado).</li>
 *   <li>{@code restaurantId} → {@code catalog.restaurant.id} de la tienda.</li>
 * </ul>
 *
 * @param orderId       id numérico público de la orden.
 * @param status        estado de la orden ({@code ENTREGADO}, {@code CANCELADO}, ...).
 * @param clientId      id de usuario (auth) del cliente de la orden.
 * @param restaurantId  id de restaurante (catalog) de la orden.
 * @param courierUserId id de usuario (auth) del repartidor, o {@code null}.
 */
public record OrderSnapshot(
        Long orderId,
        String status,
        Long clientId,
        Long restaurantId,
        Long courierUserId
) {

    private static final String DELIVERED_STATUS = "ENTREGADO";

    /**
     * @return {@code true} si la orden está {@code ENTREGADO} (S3). Solo una
     *         orden entregada habilita calificar.
     */
    public boolean isDelivered() {
        return status != null && DELIVERED_STATUS.equalsIgnoreCase(status.trim());
    }

    /**
     * @return {@code true} si el usuario indicado es el cliente de la orden.
     */
    public boolean isClient(Long userId) {
        return userId != null && userId.equals(clientId);
    }

    /**
     * @return {@code true} si el usuario indicado es el repartidor de la orden.
     *         {@code false} si la orden no tiene repartidor asignado.
     */
    public boolean isCourier(Long userId) {
        return userId != null && courierUserId != null && userId.equals(courierUserId);
    }

    /**
     * Deriva el id del destino a partir de la orden (S7).
     *
     * @param target tipo de destino.
     * @return el id del destino en su espacio (catalog para {@code TIENDA}, auth
     *         para {@code REPARTIDOR}/{@code CLIENTE}), o {@code null} si la
     *         orden no tiene ese participante (p. ej. sin repartidor asignado).
     */
    public Long targetIdFor(RatingTarget target) {
        if (target == null) {
            return null;
        }
        return switch (target) {
            case TIENDA -> restaurantId;
            case REPARTIDOR -> courierUserId;
            case CLIENTE -> clientId;
        };
    }
}
