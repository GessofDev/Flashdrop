package cl.flashdrop.orders.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para el intercambio de eventos asíncronos.
 *
 * Configura el Exchange de órdenes, las colas y los bindings, además del
 * convertidor JSON para la transmisión correcta de los DTOs de eventos.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${orders.rabbitmq.exchange:orders.exchange}")
    private String exchangeName;

    @Value("${orders.rabbitmq.queue.order-created:orders.queue.created}")
    private String orderCreatedQueueName;

    @Value("${orders.rabbitmq.queue.order-status-updated:orders.queue.status-updated}")
    private String orderStatusUpdatedQueueName;

    @Value("${orders.rabbitmq.routing-key.order-created:order.created}")
    private String orderCreatedRoutingKey;

    @Value("${orders.rabbitmq.routing-key.order-status-updated:order.status.updated}")
    private String orderStatusUpdatedRoutingKey;

    @Bean
    public TopicExchange ordersExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return new Queue(orderCreatedQueueName, true);
    }

    @Bean
    public Queue orderStatusUpdatedQueue() {
        return new Queue(orderStatusUpdatedQueueName, true);
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderCreatedQueue)
                .to(ordersExchange)
                .with(orderCreatedRoutingKey);
    }

    @Bean
    public Binding orderStatusUpdatedBinding(Queue orderStatusUpdatedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderStatusUpdatedQueue)
                .to(ordersExchange)
                .with(orderStatusUpdatedRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
