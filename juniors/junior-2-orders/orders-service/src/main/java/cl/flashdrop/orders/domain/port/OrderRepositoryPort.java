package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia de pedidos.
 *
 * Define el contrato que el dominio necesita para almacenar y consultar pedidos.
 * La implementaci\u00F3n actual es un cliente REST a Supabase/PostgREST; no
 * incluye gestion de rutas de delivery (esa responsabilidad pas\u00F3 a
 * {@link DeliveryPort}, resuelta v\u00EDa HTTP hacia Delivery).
 */
public interface OrderRepositoryPort {

    /**
     * Persiste un nuevo pedido y retorna la entidad con ID asignado.
     */
    Order save(Order order);

    /**
     * Busca un pedido por su ID.
     */
    Optional<Order> findById(UUID id);

    /**
     * Lista todos los pedidos, opcionalmente filtrados por restaurante.
     *
     * @param restaurantId null para listar todos los pedidos
     */
    List<Order> findAll(UUID restaurantId);

    /**
     * Actualiza \u00FAnicamente el estado de un pedido.
     */
    void updateStatus(UUID orderId, OrderStatus status);

    /**
     * Actualiza el repartidor asignado y el estado de m\u00FAltiples pedidos a la vez.
     *
     * @param orderIds   IDs de los pedidos a actualizar
     * @param deliveryId ID del repartidor que toma los pedidos
     * @param status     nuevo estado que se aplicar\u00E1
     * @return n\u00FAmero de pedidos actualizados exitosamente
     */
    int claimOrders(List<UUID> orderIds, UUID deliveryId, OrderStatus status);

    /**
     * Cuenta los pedidos activos (EN_CAMINO o RETIRADO) de un repartidor.
     */
    int countActiveOrdersByDelivery(UUID deliveryId);

    /**
     * Identifica que todos los pedidos indicados existen y no han sido tomados.
     *
     * @return la lista completa si son v\u00E1lidos
     */
    List<Order> findByIdsForClaim(List<UUID> orderIds);

    /**
     * Busca varios pedidos por su ID (para uso interno y futuro endpoint interno).
     *
     * @return los pedidos existentes (ordenes inexistentes se omiten)
     */
    List<Order> findByIds(List<UUID> orderIds);
}
