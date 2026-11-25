package com.flashdrop.reviews.application.port.outbound;

import java.util.Optional;

/**
 * Puerto de salida para resolver el restaurante de un dueño de tienda.
 *
 * <p>Implementado por el cliente HTTP contra
 * {@code GET /api/internal/restaurants?userId={userId}} de catalog-service.</p>
 *
 * <p>Hace falta para validar la <b>participación</b> del rol {@code Restaurante}
 * en la matriz §4: un usuario con rol {@code Restaurante} solo puede calificar
 * dentro de las órdenes de <b>su</b> tienda. Sin este dato, cualquier dueño de
 * tienda podría calificar cualquier repartidor (IDOR).</p>
 */
public interface RestaurantLookupPort {

    /**
     * @param ownerUserId id de usuario (auth) del dueño de la tienda.
     * @return el id del restaurante (catalog) del dueño, o vacío si no tiene ninguno.
     */
    Optional<Long> findRestaurantIdByOwner(Long ownerUserId);

    /**
     * Verifica si el usuario opera el restaurante indicado (dueño o encargado
     * del merchant al que pertenece el local).
     *
     * @param userId id de usuario (auth).
     * @param restaurantId id del restaurante (catalog).
     * @return {@code true} si el usuario opera ese local, {@code false} en caso contrario.
     */
    boolean checkRestaurantMembership(Long userId, Long restaurantId);
}
