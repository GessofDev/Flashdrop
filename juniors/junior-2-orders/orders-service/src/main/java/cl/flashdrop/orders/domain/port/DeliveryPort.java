package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.ClientInfo;
import cl.flashdrop.orders.domain.model.DeliveryInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para consultar información de Clientes y Repartidores.
 *
 * En esta fase inicial, la implementación {@code DeliveryJpaAdapter} consulta
 * directamente la base de datos compartida para obtener perfiles de usuarios.
 *
 * En el futuro, este puerto puede implementarse como un cliente HTTP/gRPC
 * hacia auth-service o delivery-service sin modificar ningún caso de uso.
 */
public interface DeliveryPort {

    /**
     * Obtiene el ID de perfil de cliente asociado a un usuario.
     * Si el userId es null, retorna el primer cliente disponible (modo demo).
     */
    Optional<UUID> findClientIdByUserId(UUID userId);

    /**
     * Obtiene la información completa del cliente por su ID de perfil.
     */
    Optional<ClientInfo> findClientById(UUID clientId);

    /**
     * Obtiene el ID de perfil de repartidor asociado a un usuario.
     */
    Optional<UUID> findDeliveryIdByUserId(UUID userId);

    /**
     * Obtiene la información completa de un repartidor por su ID de perfil.
     */
    Optional<DeliveryInfo> findDeliveryById(UUID deliveryId);
}
