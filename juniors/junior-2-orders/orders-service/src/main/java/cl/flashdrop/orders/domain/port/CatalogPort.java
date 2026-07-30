package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.ProductInfo;
import cl.flashdrop.orders.domain.model.RestaurantInfo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida para consultar información del Catálogo de Productos.
 *
 * En esta fase inicial, la implementación {@code CatalogJpaAdapter} consulta
 * directamente la base de datos compartida para obtener precios actualizados.
 *
 * En el futuro, este puerto puede implementarse como un cliente HTTP/gRPC
 * hacia catalog-service sin modificar ningún caso de uso ni regla de negocio.
 */
public interface CatalogPort {

    /**
     * Obtiene la información actualizada de una lista de productos por sus IDs.
     * El precio siempre debe obtenerse desde el catálogo; nunca del cliente.
     *
     * @param productIds lista de IDs de productos
     * @return lista de ProductInfo con precio actualizado
     */
    List<ProductInfo> findProductsByIds(List<UUID> productIds);

    /**
     * Obtiene la información de un restaurante por su ID.
     */
    Optional<RestaurantInfo> findRestaurantById(UUID restaurantId);

    /**
     * Obtiene el ID del restaurante del primer restaurante asociado a un usuario.
     * Utilizado para filtrar pedidos por dueño de restaurante.
     */
    Optional<UUID> findRestaurantIdByUserId(UUID userId);
}
