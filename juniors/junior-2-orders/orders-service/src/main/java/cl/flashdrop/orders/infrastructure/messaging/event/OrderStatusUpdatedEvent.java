package cl.flashdrop.orders.infrastructure.messaging.event;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento publicado cuando se actualiza el estado de un pedido.
 *
 * Routing key: {@code order.status.updated}
 * Exchange: {@code orders.exchange}
 */
@Getter
@Builder
public class OrderStatusUpdatedEvent {

    private final UUID orderId;
    private final String previousStatus;
    private final String newStatus;

    @Builder.Default
    private final OffsetDateTime occurredAt = OffsetDateTime.now();
}
