package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.exception.StatusTransitionForbiddenException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.model.RoleTransitionPolicy;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.messaging.event.OrderStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso: Actualizar Estado de Pedido.
 *
 * Valida que el estado sea permitido y que la transición sea válida
 * antes de persistir y publicar el evento correspondiente.
 *
 * <p>PR-orders-status-authz (spec FR-4): además valida quién hace el cambio.
 * <ol>
 *   <li>Rol: alguno de los roles del usuario debe poder fijar el estado solicitado
 *       ({@link RoleTransitionPolicy}) — si no, 403.</li>
 *   <li>Ownership según el rol que concede el permiso:
 *     <ul>
 *       <li>Restaurante: el pedido debe ser de su restaurante, resuelto vía
 *           {@link CatalogPort#findRestaurantIdByUserId} — si no, 403 (cierra el IDOR del
 *           riesgo #15 del plan).</li>
 *       <li>Repartidor: el pedido debe estar asignado a él, resuelto vía
 *           {@link DeliveryPort#findDeliveryIdByUserId} — si no, 403 (acuerdo con
 *           delivery-service).</li>
 *     </ul></li>
 *   <li>Transición: el estado actual debe permitir pasar al solicitado
 *       ({@link Order#validateStatusTransition}) — si no, 409.</li>
 * </ol>
 * Ambos 403 se evalúan antes del 409; el rol va antes que el ownership porque es el que
 * indica qué ownership aplica (un usuario multirol actúa como Restaurante o como
 * Repartidor según el estado que fija).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateOrderStatusUseCase {

    private final OrderRepositoryPort orderRepository;
    private final DeliveryPort deliveryPort;
    private final EventPublisherPort eventPublisher;
    private final CatalogPort catalogPort;

    @Value("${orders.rabbitmq.routing-key.order-status-updated:order.status.updated}")
    private String statusUpdatedRoutingKey;

    @Transactional
    public void execute(UUID orderId, String rawStatus, Set<Role> currentRoles, UUID currentUserId) {
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.fromValue(rawStatus);
        } catch (IllegalArgumentException e) {
            throw new OrderDomainException("Estado no valido: " + rawStatus);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));

        // Autorización por rol y ownership del restaurante
        Set<Role> grantingRoles = RoleTransitionPolicy.rolesAllowedToSet(currentRoles, newStatus);
        if (grantingRoles.isEmpty()) {
            throw new StatusTransitionForbiddenException(
                    "Tu rol no permite cambiar un pedido a '" + newStatus.getValue() + "'");
        }
        if (grantingRoles.contains(Role.RESTAURANTE)) {
            validateRestaurantOwnership(order, currentUserId);
        }
        if (grantingRoles.contains(Role.REPARTIDOR)) {
            validateDeliveryAssignment(order, currentUserId);
        }

        // Validar transición de estado según reglas de dominio
        order.validateStatusTransition(newStatus);

        // Persistir el nuevo estado
        orderRepository.updateStatus(orderId, newStatus);

        // Sincronizar la ruta de entrega con el mismo estado
        deliveryPort.updateRouteStatusByOrder(orderId, newStatus.getValue());

        // Publicar evento
        eventPublisher.publish(statusUpdatedRoutingKey, OrderStatusUpdatedEvent.builder()
                .orderId(orderId)
                .previousStatus(order.getStatus().getValue())
                .newStatus(newStatus.getValue())
                .build());

        log.info("Estado del pedido {} actualizado: {} -> {}", orderId,
                order.getStatus().getValue(), newStatus.getValue());
    }

    private void validateRestaurantOwnership(Order order, UUID currentUserId) {
        UUID ownedRestaurantId = catalogPort.findRestaurantIdByUserId(currentUserId).orElse(null);
        if (ownedRestaurantId == null || !ownedRestaurantId.equals(order.getRestaurantId())) {
            throw new StatusTransitionForbiddenException("No puedes modificar pedidos de otro restaurante");
        }
    }

    /**
     * Acuerdo con delivery-service: el Repartidor solo puede cambiar el estado de los pedidos
     * asignados a él en el claim ({@code Order.deliveryId} == su {@code delivery.id}). Un
     * pedido sin repartidor asignado tampoco se puede retirar: primero hay que tomarlo.
     */
    private void validateDeliveryAssignment(Order order, UUID currentUserId) {
        UUID currentDeliveryId = deliveryPort.findDeliveryIdByUserId(currentUserId).orElse(null);
        if (currentDeliveryId == null || !currentDeliveryId.equals(order.getDeliveryId())) {
            throw new StatusTransitionForbiddenException("No puedes modificar pedidos asignados a otro repartidor");
        }
    }
}
