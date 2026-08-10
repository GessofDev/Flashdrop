package cl.flashdrop.orders.domain.port;

/**
 * Puerto de salida para publicar eventos de dominio de Pedidos.
 *
 * Permite que los casos de uso notifiquen eventos relevantes sin acoplarse
 * a la implementación de mensajería (RabbitMQ en esta fase).
 *
 * En el futuro puede implementarse con Kafka u otro mecanismo
 * sin modificar ningún caso de uso.
 */
public interface EventPublisherPort {

    /**
     * Publica un evento en el broker de mensajería.
     *
     * @param routingKey la clave de enrutamiento del evento (ej: "order.created")
     * @param event      el objeto evento que será serializado a JSON
     */
    void publish(String routingKey, Object event);
}
