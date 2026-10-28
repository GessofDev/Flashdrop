package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.application.dto.DeliveryCodeVerification;
import cl.flashdrop.orders.application.dto.DeliveryCodeVerification.Outcome;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.exception.StatusTransitionForbiddenException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Role;
import cl.flashdrop.orders.domain.model.RoleTransitionPolicy;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.DeliveryCodePort;
import cl.flashdrop.orders.domain.port.DeliveryPort;
import cl.flashdrop.orders.domain.port.EventPublisherPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.infrastructure.messaging.event.OrderStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso: confirmar la entrega de un pedido con el código del cliente.
 *
 * <p>Es el ÚNICO camino hacia ENTREGADO: {@link UpdateOrderStatusUseCase} rechaza ese estado.
 * El repartidor escribe el código que le dicta el cliente; solo si coincide el pedido pasa a
 * ENTREGADO y el código no puede volver a usarse (ENTREGADO es terminal).</p>
 *
 * <p>Reglas de intentos (modo SEGURO): 3 intentos por código; al fallar el tercero el código se
 * anula y se emite uno nuevo; un pedido tiene como máximo 2 códigos, agotados los cuales queda
 * bloqueado. La fila del pedido se lee con bloqueo para que intentos simultáneos no superen el
 * límite. En modo NORMAL el código es siempre 000000 y los intentos no se cuentan.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyDeliveryCodeUseCase {

    private final OrderRepositoryPort orderRepository;
    private final DeliveryPort deliveryPort;
    private final ClientPort clientPort;
    private final DeliveryCodePort deliveryCodePort;
    private final EventPublisherPort eventPublisher;

    @Value("${orders.rabbitmq.routing-key.order-status-updated:order.status.updated}")
    private String statusUpdatedRoutingKey;

    @Transactional
    public DeliveryCodeVerification execute(UUID orderId, String providedCode,
                                            Set<Role> currentRoles, UUID currentUserId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));

        if (RoleTransitionPolicy.rolesAllowedToSet(currentRoles, OrderStatus.ENTREGADO).isEmpty()) {
            throw new StatusTransitionForbiddenException("Solo un repartidor puede confirmar la entrega");
        }
        UUID currentDeliveryId = deliveryPort.findDeliveryIdByUserId(currentUserId).orElse(null);
        if (currentDeliveryId == null || !currentDeliveryId.equals(order.getDeliveryId())) {
            throw new StatusTransitionForbiddenException("No puedes modificar pedidos asignados a otro repartidor");
        }
        if (clientPort.findClientIdByUserId(currentUserId).filter(c -> c.equals(order.getClientId())).isPresent()) {
            throw new StatusTransitionForbiddenException("No puedes entregarte tu propio pedido");
        }

        // 409 si el pedido no esta RETIRADO/EN_CAMINO (incluye el ya ENTREGADO: codigo de un solo uso)
        order.validateStatusTransition(OrderStatus.ENTREGADO);

        if (order.isDeliveryCodeLocked()) {
            return new DeliveryCodeVerification(Outcome.LOCKED, 0);
        }

        if (matches(deliveryCodePort.currentCodeFor(order), providedCode)) {
            order.setDeliveryCodeVerifiedAt(OffsetDateTime.now());
            orderRepository.updateDeliveryCodeState(order);
            orderRepository.recordDeliveryCodeAttempt(orderId, currentDeliveryId, order.getDeliveryCodeVersion(), true);
            orderRepository.updateStatus(orderId, OrderStatus.ENTREGADO);
            deliveryPort.updateRouteStatusByOrder(orderId, OrderStatus.ENTREGADO.getValue());
            eventPublisher.publish(statusUpdatedRoutingKey, OrderStatusUpdatedEvent.builder()
                    .orderId(orderId)
                    .previousStatus(order.getStatus().getValue())
                    .newStatus(OrderStatus.ENTREGADO.getValue())
                    .build());
            log.info("Pedido {} entregado con codigo de verificacion", orderId);
            return new DeliveryCodeVerification(Outcome.DELIVERED, order.remainingCodeAttempts());
        }

        if (!order.isSecureReception()) {
            // Modo NORMAL: el codigo es publico (000000), no hay nada que proteger ni que bloquear.
            return new DeliveryCodeVerification(Outcome.WRONG_CODE, Order.MAX_CODE_ATTEMPTS);
        }

        int failedVersion = order.getDeliveryCodeVersion();
        boolean renewed = order.registerFailedCodeAttempt();
        orderRepository.updateDeliveryCodeState(order);
        orderRepository.recordDeliveryCodeAttempt(orderId, currentDeliveryId, failedVersion, false);

        if (order.isDeliveryCodeLocked()) {
            log.warn("Pedido {} BLOQUEADO para entrega: se agotaron los intentos de todos sus codigos", orderId);
            return new DeliveryCodeVerification(Outcome.LOCKED, 0);
        }
        if (renewed) {
            log.warn("Pedido {}: intentos agotados, codigo de entrega renovado (version {})",
                    orderId, order.getDeliveryCodeVersion());
            return new DeliveryCodeVerification(Outcome.CODE_RENEWED, order.remainingCodeAttempts());
        }
        return new DeliveryCodeVerification(Outcome.WRONG_CODE, order.remainingCodeAttempts());
    }

    /** Comparación en tiempo constante, para no filtrar por tiempo cuántos dígitos acertó. */
    private static boolean matches(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.trim().getBytes(StandardCharsets.UTF_8));
    }
}
