package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.DeliveryInfo;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.ClientEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataClientRepository;
import cl.flashdrop.orders.infrastructure.api.dto.response.InternalOrderSummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controlador interno de Orders para el resumen de una orden (T4 del feature
 * {@code ratings-system}).
 *
 * <p>Expone {@code GET /api/internal/orders/{id}} protegido por
 * {@code X-Internal-Api-Key} (el {@code InternalApiKeyFilter} de este servicio
 * cubre todo el prefijo {@code /api/internal/}). Lo consume
 * {@code reviews-service} para validar la participación del autor en la orden y
 * derivar el destino de la calificación (S7).</p>
 *
 * <p>El id del path acepta el id numérico público o el UUID interno, con el
 * mismo criterio que {@code GET /api/orders/{id}} ({@link OrderIdResolver}).</p>
 *
 * <p><b>No expone datos de contacto</b>: ni dirección, ni teléfono, ni nombre del
 * repartidor, ni items, ni importes.</p>
 *
 * <p>Nota de arquitectura: la resolución del id de usuario del cliente usa
 * {@link SpringDataClientRepository} directamente. Es el mismo criterio que ya
 * usa {@link OrderIdResolver} (que importa {@link IdConverter}, de
 * {@code infrastructure.adapter.outbound}) y evita tocar los puertos de dominio,
 * que quedan fuera de la superficie de este cambio. El repositorio se resuelve
 * de forma opcional porque solo existe en los perfiles con JPA (el perfil
 * {@code supabase}, en transición, no lo tiene).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/orders")
public class InternalOrderSummaryController {

    private final OrderRepositoryPort orderRepository;
    private final ObjectProvider<SpringDataClientRepository> clientRepositoryProvider;

    public InternalOrderSummaryController(OrderRepositoryPort orderRepository,
                                          ObjectProvider<SpringDataClientRepository> clientRepositoryProvider) {
        this.orderRepository = orderRepository;
        this.clientRepositoryProvider = clientRepositoryProvider;
    }

    /**
     * @param orderId id de la orden: numérico público o UUID interno.
     * @return el resumen de la orden con sus participantes en id numérico público.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<InternalOrderSummaryResponse> getOrderSummary(@PathVariable String orderId) {
        UUID internalId = OrderIdResolver.resolve(orderId);

        Order order = orderRepository.findById(internalId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado: " + orderId));

        InternalOrderSummaryResponse response = new InternalOrderSummaryResponse(
                IdConverter.toLong(order.getId()),
                order.getStatus() != null ? order.getStatus().name() : null,
                resolveClientUserId(order),
                order.getRestaurantId() != null ? IdConverter.toLong(order.getRestaurantId()) : null,
                resolveCourierUserId(order));

        log.debug("GET /api/internal/orders/{} -> orderId={} status={}",
                orderId, response.orderId(), response.status());
        return ResponseEntity.ok(response);
    }

    /**
     * Resuelve el {@code auth.users.id} del cliente de la orden.
     *
     * <p>{@code orders.client_id} apunta a {@code public.client.id} (el perfil de
     * cliente), que es un espacio de ids distinto del de Auth. Para que el
     * consumidor pueda comparar contra el {@code sub} del JWT y derivar el
     * destino {@code CLIENTE}, se resuelve el {@code user_id} del perfil.</p>
     *
     * @return el id de usuario del cliente, o {@code null} si no se puede
     *         resolver (perfil de cliente anonimizado o perfil sin repositorio JPA).
     */
    private Long resolveClientUserId(Order order) {
        UUID clientId = order.getClientId();
        if (clientId == null) {
            return null;
        }
        SpringDataClientRepository clientRepository = clientRepositoryProvider.getIfAvailable();
        if (clientRepository == null) {
            log.warn("No hay repositorio JPA de client disponible: no se puede resolver el id de "
                    + "usuario del cliente de la orden {}", IdConverter.toLong(order.getId()));
            return null;
        }
        return clientRepository.findById(IdConverter.toLong(clientId))
                .map(ClientEntity::getUserId)
                .orElse(null);
    }

    /**
     * Resuelve el {@code auth.users.id} del repartidor de la orden desde
     * {@code Order.deliveryInfo.userId}.
     *
     * @return el id de usuario del repartidor, o {@code null} si la orden no
     *         tiene repartidor asignado.
     */
    private Long resolveCourierUserId(Order order) {
        DeliveryInfo deliveryInfo = order.getDeliveryInfo();
        if (deliveryInfo == null || deliveryInfo.getUserId() == null) {
            return null;
        }
        return IdConverter.toLong(deliveryInfo.getUserId());
    }
}
