package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.messaging.event.OrderStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Caso de uso: Actualizar Estado de Pedido.
 *
 * Valida que el estado sea permitido y que la transición sea válida
 * antes de persistir y publicar el evento correspondiente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateOrderStatusUseCase {

    private final OrderRepositoryPort orderRepository;
    private final EventPublisherPort eventPublisher;

    @Value("${orders.rabbitmq.routing-key.order-status-updated:order.status.updated}")
    private String statusUpdatedRoutingKey;

    @Transactional
    public void execute(UUID orderId, String rawStatus) {
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.fromValue(rawStatus);
        } catch (IllegalArgumentException e) {
            throw new OrderDomainException("Estado no valido: " + rawStatus);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));

        // Validar transición de estado según reglas de dominio
        order.validateStatusTransition(newStatus);

        // Persistir el nuevo estado
        orderRepository.updateStatus(orderId, newStatus);

        // Sincronizar la ruta de entrega con el mismo estado
        orderRepository.updateRouteStatusByOrder(orderId, newStatus.getValue());

        // Publicar evento
        eventPublisher.publish(statusUpdatedRoutingKey, OrderStatusUpdatedEvent.builder()
                .orderId(orderId)
                .previousStatus(order.getStatus().getValue())
                .newStatus(newStatus.getValue())
                .build());

        log.info("Estado del pedido {} actualizado: {} -> {}", orderId,
                order.getStatus().getValue(), newStatus.getValue());
    }
}
