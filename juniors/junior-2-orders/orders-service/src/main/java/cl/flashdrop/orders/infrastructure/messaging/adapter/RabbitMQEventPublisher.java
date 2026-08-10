package cl.flashdrop.orders.infrastructure.messaging.adapter;

import cl.flashdrop.orders.domain.port.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implementación del puerto de publicación de eventos usando RabbitMQ.
 *
 * Serializa los eventos a JSON y los publica en el exchange de órdenes.
 * Si RabbitMQ no está disponible, el error es logueado como warning
 * para no interrumpir el flujo de negocio principal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQEventPublisher implements EventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Value("${orders.rabbitmq.exchange:orders.exchange}")
    private String exchange;

    @Override
    public void publish(String routingKey, Object event) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.debug("Evento publicado: exchange={}, routingKey={}", exchange, routingKey);
        } catch (Exception e) {
            // No interrumpir el flujo principal si RabbitMQ no está disponible
            log.warn("No se pudo publicar el evento {} en RabbitMQ: {}", routingKey, e.getMessage());
        }
    }
}
