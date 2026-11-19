package cl.flashdrop.orders.application.usecase;

import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.Payment;
import cl.flashdrop.orders.domain.model.PaymentStatus;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.domain.port.PaymentGatewayPort;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Caso de uso: como va el pago de un pedido (para que la app sepa si ya puede pasar a "Nuevo pedido"). */
@Service
@RequiredArgsConstructor
public class GetOrderPaymentUseCase {

    /** {@code paymentStatus} es el del ultimo intento; {@code paymentUrl} solo si hay un cobro abierto. */
    public record PaymentView(String orderStatus, String paymentStatus, Long paymentId, String paymentUrl,
                              OffsetDateTime paymentExpiresAt) {
    }

    private final OrderRepositoryPort orderRepository;
    private final ClientPort clientPort;
    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayPort paymentGateway;

    @Transactional(readOnly = true)
    public PaymentView execute(UUID orderId, UUID currentUserId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));
        UUID clientId = clientPort.findClientIdByUserId(currentUserId).orElse(null);
        if (clientId == null || !clientId.equals(order.getClientId())) {
            throw new ForbiddenOperationException("Solo el cliente que hizo el pedido puede ver su pago");
        }
        List<Payment> payments = paymentRepository.findByOrderId(orderId);
        Payment latest = payments.isEmpty() ? null : payments.get(0);
        boolean open = latest != null && latest.getStatus() == PaymentStatus.PENDIENTE;
        return new PaymentView(order.getStatus().getValue(),
                latest == null ? null : latest.getStatus().name(),
                latest == null ? null : latest.getId(),
                open ? paymentGateway.paymentUrlFor(latest.getProviderTxId()) : null,
                order.getPaymentExpiresAt());
    }
}
