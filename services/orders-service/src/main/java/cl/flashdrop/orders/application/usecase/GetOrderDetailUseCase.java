package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.OrderEnricher;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryCodePort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso: Obtener Detalle de un Pedido.
 *
 * <p>Solo puede ver un pedido: el cliente que lo hizo, el dueño del restaurante al que
 * pertenece, o el repartidor asignado (o, mientras nadie lo ha tomado, cualquier repartidor
 * si está listo para retiro). Cualquier otro caso es 403.</p>
 *
 * <p>Enriquece el pedido con información referencial de otros servicios (cliente,
 * restaurante) a través de los ports de dominio, manteniendo el repositorio
 * limitado a sus tablas propias.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetOrderDetailUseCase {

    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;
    private final ClientPort clientPort;
    private final DeliveryPort deliveryPort;
    private final OrderEnricher enricher;
    private final DeliveryCodePort deliveryCodePort;

    @Transactional(readOnly = true)
    public Order execute(UUID orderId, Set<Role> currentRoles, UUID currentUserId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));
        if (!canView(order, currentRoles, currentUserId)) {
            throw new ForbiddenOperationException("No puedes consultar este pedido");
        }
        enricher.enrich(order);
        // El codigo lo ve SOLO el cliente dueño, en modo SEGURO y hasta que se entrega. Ni el
        // repartidor ni el restaurante lo ven jamas.
        if (order.isSecureReception() && order.getStatus() != OrderStatus.ENTREGADO
                && isOwnerClient(order, currentRoles, currentUserId)) {
            order.setDeliveryCode(deliveryCodePort.currentCodeFor(order));
        }
        log.debug("Detalle de pedido {} enriquecido", orderId);
        return order;
    }

    private boolean isOwnerClient(Order order, Set<Role> roles, UUID userId) {
        return roles.contains(Role.CLIENTE)
                && clientPort.findClientIdByUserId(userId).filter(c -> c.equals(order.getClientId())).isPresent();
    }

    private boolean canView(Order order, Set<Role> roles, UUID userId) {
        if (roles.contains(Role.CLIENTE)
                && clientPort.findClientIdByUserId(userId).filter(c -> c.equals(order.getClientId())).isPresent()) {
            return true;
        }
        if (roles.contains(Role.RESTAURANTE)
                && catalogPort.findRestaurantIdByUserId(userId).filter(r -> r.equals(order.getRestaurantId())).isPresent()) {
            return true;
        }
        if (roles.contains(Role.REPARTIDOR)) {
            if (order.getDeliveryId() == null) {
                return order.getStatus() == OrderStatus.LISTO_PARA_RETIRO;
            }
            return deliveryPort.findDeliveryIdByUserId(userId)
                    .filter(d -> d.equals(order.getDeliveryId())).isPresent();
        }
        return false;
    }
}
