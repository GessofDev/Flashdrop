package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.DeliveryRoute;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida hacia Delivery Service.
 *
 * <p>Orders ya no persiste ni consulta {@code delivery_routes} directamente sobre Supabase.
 * Todas las operaciones de rutas y perfiles de reparto se resuelven vía API interna de
 * delivery-service (contratos C-5, C-6 y C-7).</p>
 */
public interface DeliveryPort {

    /**
     * Obtiene el ID de perfil de repartidor (delivery) asociado a un usuario.
     * Contrato interno: GET /api/internal/delivery-persons?userId={userId} (C-5).
     *
     * @param userId id de usuario en Auth
     * @return el UUID del perfil de repartidor, o vacío si el usuario no es repartidor
     */
    Optional<UUID> findDeliveryIdByUserId(UUID userId);

    /**
     * Crea la ruta de entrega para un pedido.
     * Contrato interno: POST /api/internal/routes (C-6).
     */
    void saveRoute(DeliveryRoute route);

    /**
     * Sincroniza el estado de la ruta de un pedido al cambiar el estado del pedido.
     * Contrato interno: PATCH /api/internal/routes/order/{orderId}/status (C-7, resuelto en
     * commit 904464d de delivery-service — ver GAP-01/GAP-01b).
     */
    void updateRouteStatusByOrder(UUID orderId, String status);

    /**
     * Sincroniza el estado de las rutas de varios pedidos tomados en bloque.
     * No existe variante bulk en Delivery: internamente hace una llamada
     * PATCH /api/internal/routes/order/{orderId}/status por cada orderId (C-7).
     */
    void updateRouteStatus(List<UUID> orderIds, String status);
}
