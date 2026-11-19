package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.OrderStatus;
import cl.flashdrop.orders.domain.model.Payment;
import cl.flashdrop.orders.domain.model.PaymentStatus;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.domain.port.PaymentGatewayPort;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Caso de uso: abrir un nuevo intento de pago para el mismo pedido (F8), por ejemplo tras un pago
 * rechazado. Si ya hay un cobro abierto, se devuelve ese en vez de abrir otro.
 */
@Service
@RequiredArgsConstructor
public class RetryPaymentUseCase {

    /** Cobro abierto que la app debe pagar. */
    public record OpenPayment(Long paymentId, String paymentUrl, OffsetDateTime paymentExpiresAt) {
    }

    private final OrderRepositoryPort orderRepository;
    private final ClientPort clientPort;
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;
    private final Clock clock;

    @Transactional
    public OpenPayment execute(UUID orderId, UUID currentUserId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));
        requireOwner(order, currentUserId);
        if (order.getStatus() != OrderStatus.PENDIENTE_PAGO) {
            throw new OrderDomainException("El pedido no esta pendiente de pago");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (order.getPaymentExpiresAt() != null && !order.getPaymentExpiresAt().isAfter(now)) {
            throw new OrderDomainException("El plazo para pagar este pedido ya vencio");
        }

        Payment open = paymentRepository.findByOrderId(orderId).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDIENTE).findFirst().orElse(null);
        if (open == null) {
            PaymentGatewayPort.PaymentSession session = paymentGateway.createSession(orderId, order.getTotal(), "CLP");
            open = paymentRepository.save(Payment.open(
                    orderId, paymentGateway.providerName(), session.providerTxId(), order.getTotal(), now));
        }
        return new OpenPayment(open.getId(), paymentGateway.paymentUrlFor(open.getProviderTxId()),
                order.getPaymentExpiresAt());
    }

    private void requireOwner(Order order, UUID userId) {
        UUID clientId = clientPort.findClientIdByUserId(userId).orElse(null);
        if (clientId == null || !clientId.equals(order.getClientId())) {
            throw new ForbiddenOperationException("Solo el cliente que hizo el pedido puede pagarlo");
        }
    }
}
