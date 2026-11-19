package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.DeliveryRoute;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Payment;
import cl.flashdrop.orders.domain.model.PaymentStatus;
import cl.flashdrop.orders.domain.model.RestaurantInfo;
import cl.flashdrop.orders.domain.port.CatalogPort;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import cl.flashdrop.orders.infrastructure.messaging.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Caso de uso: aplicar el resultado de un pago que informa la pasarela (F8).
 *
 * <ul>
 *   <li><b>Aprobado:</b> el pago queda aprobado y el pedido pasa de "Pendiente de pago" directo a
 *       "Preparando" (la tienda toma todo pedido pagado, sin aceptarlo a mano). La espera de repartidor no corre
 *       todavia: empieza cuando el pedido queda listo para retiro. Recién
 *       ahí se crea la ruta de entrega y se publica {@code order.created}.</li>
 *   <li><b>Rechazado:</b> el pago queda rechazado y el pedido sigue pendiente; el cliente puede
 *       reintentar con el mismo pedido ({@link RetryPaymentUseCase}).</li>
 * </ul>
 *
 * <p>Es idempotente: las pasarelas reenvian los avisos. Repetir el mismo resultado no cambia nada.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmPaymentUseCase {

    public enum Outcome {
        /** Pago aprobado y pedido activado (ruta creada, evento publicado). */
        ORDER_ACTIVATED,
        /** Pago aprobado de un pedido que ya estaba activado (aviso repetido). */
        ALREADY_CONFIRMED,
        /** Pago aprobado, pero el pedido ya no estaba pendiente (p. ej. cancelado). Requiere revision. */
        ORDER_NOT_PENDING,
        /** Pago rechazado: el pedido sigue pendiente de pago. */
        PAYMENT_REJECTED
    }

    private final PaymentRepositoryPort paymentRepository;
    private final OrderRepositoryPort orderRepository;
    private final CatalogPort catalogPort;
    private final ClientPort clientPort;
    private final DeliveryPort deliveryPort;
    private final EventPublisherPort eventPublisher;
    private final Clock clock;

    @Value("${orders.default-distance-km:3.2}")
    private java.math.BigDecimal defaultDistanceKm;

    @Value("${orders.default-estimated-minutes:20}")
    private int defaultEstimatedMinutes;

    @Value("${orders.rabbitmq.routing-key.order-created:order.created}")
    private String orderCreatedRoutingKey;

    @Transactional
    public Outcome execute(String provider, String providerTxId, boolean approved, String rawStatus) {
        Payment payment = paymentRepository.findByProviderAndProviderTxId(provider, providerTxId)
                .orElseThrow(() -> new OrderDomainException("Pago no encontrado"));
        // Se bloquea la fila del pedido para que dos avisos simultaneos se procesen de a uno.
        Order order = orderRepository.findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (!approved) {
            if (payment.getStatus() == PaymentStatus.APROBADO) {
                throw new OrderDomainException("El pago ya fue aprobado y no puede pasar a rechazado");
            }
            payment.reject(rawStatus, now);
            paymentRepository.save(payment);
            log.info("Pago {} del pedido {} rechazado", payment.getId(), order.getId());
            return Outcome.PAYMENT_REJECTED;
        }

        boolean alreadyApproved = payment.getStatus() == PaymentStatus.APROBADO;
        payment.approve(rawStatus, now);
        paymentRepository.save(payment);

        if (order.getStatus() != OrderStatus.PENDIENTE_PAGO) {
            if (alreadyApproved && order.getStatus() != OrderStatus.CANCELADO) {
                return Outcome.ALREADY_CONFIRMED;
            }
            // Pagaron un pedido que ya se cancelo: no se reactiva. La devolucion esta fuera de este alcance.
            log.warn("Pago {} aprobado para el pedido {} que esta en estado '{}': no se activa",
                    payment.getId(), order.getId(), order.getStatus().getValue());
            return Outcome.ORDER_NOT_PENDING;
        }

        // La tienda toma todo pedido pagado: pasa directo a Preparando. La espera de repartidor no corre todavia:
        // empieza cuando el pedido queda listo para retiro, que es cuando un repartidor puede tomarlo.
        boolean activated = orderRepository.confirmPayment(order.getId(), now);
        if (!activated) {
            log.warn("El pedido {} dejo de estar pendiente mientras se confirmaba su pago", order.getId());
            return Outcome.ORDER_NOT_PENDING;
        }

        createRoute(order);
        eventPublisher.publish(orderCreatedRoutingKey, OrderCreatedEvent.builder()
                .orderId(order.getId())
                .clientId(order.getClientId())
                .restaurantId(order.getRestaurantId())
                .total(order.getTotal())
                .status(OrderStatus.PREPARANDO.getValue())
                .build());
        log.info("Pedido {} pagado: la tienda lo toma, pasa a Preparando", order.getId());
        return Outcome.ORDER_ACTIVATED;
    }

    /** La ruta de entrega nace al aprobarse el pago: Delivery nunca ve pedidos sin pagar. */
    private void createRoute(Order order) {
        RestaurantInfo restaurant = catalogPort.findRestaurantById(order.getRestaurantId()).orElse(null);
        UUID clientUserId = clientPort.findUserIdByClientId(order.getClientId()).orElse(null);
        DeliveryRoute route = DeliveryRoute.builder()
                .orderId(order.getId())
                .pickupAddress(restaurant != null
                        ? restaurant.getName() + ", " + restaurant.getAddress()
                        : "Restaurante")
                .deliveryAddress(order.getAddress())
                .commune(order.getCommune())
                .distanceKm(order.getDistanceKm() != null ? order.getDistanceKm() : defaultDistanceKm)
                .estimatedMinutes(order.getEstimatedMinutes() != null ? order.getEstimatedMinutes() : defaultEstimatedMinutes)
                .status("Pendiente")
                .receptionMode(order.getReceptionMode().name())
                .pickupLatitude(restaurant != null ? restaurant.getLatitude() : null)
                .pickupLongitude(restaurant != null ? restaurant.getLongitude() : null)
                .clientUserId(clientUserId)
                .build();
        deliveryPort.saveRoute(route);
    }
}
