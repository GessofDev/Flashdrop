package cl.flashdrop.orders.infrastructure.messaging.event;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento publicado cuando se crea un nuevo pedido.
 *
 * Routing key: {@code order.created}
 * Exchange: {@code orders.exchange}
 *
 * Este evento permite que delivery-service (en el futuro) o cualquier
 * consumidor interesado reaccione a la creación de pedidos.
 */
@Getter
@Builder
public class OrderCreatedEvent {

    /** Identificador único del pedido */
    private final UUID orderId;

    /** ID del cliente que realizó el pedido */
    private final UUID clientId;

    /** ID del restaurante al que pertenece el pedido */
    private final UUID restaurantId;

    /** Total del pedido en CLP */
    private final BigDecimal total;

    /** Estado inicial del pedido ("Nuevo pedido") */
    private final String status;

    /** Timestamp del evento */
    @Builder.Default
    private final OffsetDateTime occurredAt = OffsetDateTime.now();
}
