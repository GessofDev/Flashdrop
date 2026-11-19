package cl.flashdrop.orders.application.service;

import cl.flashdrop.orders.domain.model.CancellationReason;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.messaging.event.OrderStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Cancela UN pedido que nadie atendio (F7), en su propia transaccion para que un fallo no arrastre a los demas.
 *
 * <p>El estado cambia con una sola sentencia condicional (sigue en el estado esperado y sin repartidor): si la
 * tienda acepto o un repartidor tomo el pedido justo antes, no se cancela. La ruta de Delivery se cierra dentro
 * de la misma transaccion: si Delivery falla se deshace la cancelacion y la tarea reintenta en el siguiente
 * ciclo, asi no queda un pedido cancelado con su ruta abierta.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnattendedOrderCanceller {

    private final OrderRepositoryPort orderRepository;
    private final DeliveryPort deliveryPort;
    private final EventPublisherPort eventPublisher;
    private final Clock clock;

    @Value("${orders.rabbitmq.routing-key.order-status-updated:order.status.updated}")
    private String statusUpdatedRoutingKey;

    /** @return {@code true} si se cancelo; {@code false} si el pedido cambio de estado o ya tiene repartidor. */
    @Transactional
    public boolean cancel(Order order, CancellationReason reason) {
        OrderStatus previous = order.getStatus();
        if (!orderRepository.cancelUnattended(order.getId(), previous, reason, OffsetDateTime.now(clock))) {
            log.info("Pedido {} ya no esta {} sin repartidor: no se cancela por {}",
                    order.getId(), previous.getValue(), reason);
            return false;
        }
        deliveryPort.closeRoute(order.getId(), OrderStatus.CANCELADO.getValue());
        eventPublisher.publish(statusUpdatedRoutingKey, OrderStatusUpdatedEvent.builder()
                .orderId(order.getId())
                .previousStatus(previous.getValue())
                .newStatus(OrderStatus.CANCELADO.getValue())
                .reason(reason.name())
                .build());
        log.info("Pedido {} cancelado automaticamente ({}): {} -> Cancelado", order.getId(), reason, previous.getValue());
        return true;
    }
}
