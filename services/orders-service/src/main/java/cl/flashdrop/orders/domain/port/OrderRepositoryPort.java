package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
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
     * Asigna el repartidor a m\u00FAltiples pedidos a la vez. NO modifica el estado de los
     * pedidos: cada uno conserva el suyo (PR-orders-claim, spec FR-3).
     *
     * @param orderIds   IDs de los pedidos a actualizar
     * @param deliveryId ID del repartidor que toma los pedidos
     * @return n\u00FAmero de pedidos actualizados exitosamente
     */
    int claimOrders(List<UUID> orderIds, UUID deliveryId);

    /**
     * Cuenta los pedidos activos de un repartidor: asignados a \u00E9l y a\u00FAn no entregados
     * (LISTO_PARA_RETIRO, RETIRADO o EN_CAMINO legacy). LISTO_PARA_RETIRO cuenta porque el
     * claim ya no muta el estado: un pedido tomado y no retirado sigue en ese estado.
     */
    int countActiveOrdersByDelivery(UUID deliveryId);

    /**
     * Identifica que todos los pedidos indicados existen y no han sido tomados.
     *
     * @return la lista completa si son v\u00E1lidos
     */
    List<Order> findByIdsForClaim(List<UUID> orderIds);

    /**
     * Pedidos de un restaurante disponibles para que un repartidor los tome
     * (PR-orders-available, spec FR-2): en LISTO_PARA_RETIRO y sin repartidor asignado
     * (el claim no muta el estado, así que un pedido tomado sigue en LISTO_PARA_RETIRO).
     * Orden FIFO por fecha de creación.
     *
     * @param limit máximo de pedidos a devolver
     */
    List<Order> findAvailableForDelivery(UUID restaurantId, int limit);

    /**
     * Pedidos de un restaurante en alguno de los estados dados, creados entre {@code from}
     * y {@code to} (ambos inclusive), con sus items (PR-orders-metrics, tasks T-23).
     */
    List<Order> findByRestaurantAndStatusAndCreatedAtBetween(
            UUID restaurantId, Collection<OrderStatus> statuses, OffsetDateTime from, OffsetDateTime to);

    /**
     * Busca varios pedidos por su ID (para uso interno y futuro endpoint interno).
     *
     * @return los pedidos existentes (ordenes inexistentes se omiten)
     */
    List<Order> findByIds(List<UUID> orderIds);
}
