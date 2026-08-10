package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.DeliveryRoute;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para la persistencia de pedidos.
 *
 * Define el contrato que el dominio necesita para almacenar y consultar órdenes.
 * La implementación puede ser JPA (fase actual), un cliente HTTP u otro mecanismo.
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
     * Actualiza únicamente el estado de un pedido.
     */
    void updateStatus(UUID orderId, OrderStatus status);

    /**
     * Actualiza el repartidor asignado y el estado de múltiples pedidos a la vez.
     *
     * @param orderIds   IDs de los pedidos a actualizar
     * @param deliveryId ID del repartidor que toma los pedidos
     * @param status     nuevo estado que se aplicará
     * @return número de pedidos actualizados exitosamente
     */
    int claimOrders(List<UUID> orderIds, UUID deliveryId, OrderStatus status);

    /**
     * Cuenta los pedidos activos (EN_CAMINO o RETIRADO) de un repartidor.
     */
    int countActiveOrdersByDelivery(UUID deliveryId);

    /**
     * Identifica que todos los pedidos indicados existen y no han sido tomados.
     *
     * @return la lista completa si son válidos
     */
    List<Order> findByIdsForClaim(List<UUID> orderIds);

    // ---------------------------------------------------
    // DeliveryRoute operations (gestionadas desde Orders)
    // ---------------------------------------------------

    /**
     * Persiste una nueva ruta de entrega.
     */
    void saveRoute(DeliveryRoute route);

    /**
     * Actualiza el estado de las rutas de los pedidos indicados.
     */
    void updateRouteStatus(List<UUID> orderIds, String status);

    /**
     * Actualiza el estado de la ruta de un único pedido.
     */
    void updateRouteStatusByOrder(UUID orderId, String status);

    /**
     * Lista todas las rutas de entrega con información de pedido asociada.
     */
    List<Order> findAllRoutesWithOrders();
}
