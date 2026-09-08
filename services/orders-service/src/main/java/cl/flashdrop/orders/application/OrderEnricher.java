package cl.flashdrop.orders.application;

import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Enriquecedor de pedidos para la capa de lectura.
 *
 * <p>El {@link cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.supabase.SupabaseRestOrderRepositoryAdapter}
 * solo conoce las tablas propias de Orders ({@code orders}, {@code order_items}). Este
 * componente aplica, sobre un {@link Order}, la información referencial que proviene de
 * otros servicios mediante los ports de dominio, manteniendo el repositorio libre de
 * dependencias HTTP.</p>
 *
 * <p>Estado pendiente (sin contrato HTTP disponible en C-1..C-7):</p>
 * <ul>
 *   <li>{@code DeliveryInfo}: no hay contrato para obtener el repartidor por su id de delivery
 *       (C-5 es por userId). Se mantiene {@code null} hasta que exista el endpoint.</li>
 *   <li>{@code DeliveryRoute}: no hay contrato para leer la ruta de un pedido. Se mantiene
 *       {@code null} hasta que exista el endpoint.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OrderEnricher {

    private final CatalogPort catalogPort;
    private final ClientPort clientPort;

    public void enrich(Order order) {
        if (order == null) {
            return;
        }
        order.setRestaurantInfo(
                catalogPort.findRestaurantById(order.getRestaurantId()).orElse(null));
        order.setClientInfo(
                clientPort.findClientById(order.getClientId()).orElse(null));

        // Sin contrato C-5 (delivery por deliveryId) ni endpoint de lectura de rutas (C-7 solo escribe).
        order.setDeliveryInfo(null);
        order.setRoute(null);
    }
}
