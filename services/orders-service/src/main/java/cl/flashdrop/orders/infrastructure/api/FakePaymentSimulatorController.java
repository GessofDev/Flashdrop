package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ConfirmPaymentUseCase;
import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.model.Order;
import cl.flashdrop.orders.domain.model.Payment;
import cl.flashdrop.orders.domain.port.ClientPort;
import cl.flashdrop.orders.domain.port.OrderRepositoryPort;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.payment.FakePaymentGateway;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pantalla de pago simulada, lado backend (F8). Solo existe con la pasarela simulada
 * ({@code orders.payment.provider=fake}): la app muestra su pantalla de pago y llama aqui para
 * "aprobar" o "rechazar". El cliente dueño del pedido es el unico que puede resolver su pago.
 * Con la pasarela real este endpoint desaparece y el resultado llega por el webhook.
 */
@RestController
@RequestMapping("/api/payments/fake")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "orders.payment.provider", havingValue = "fake", matchIfMissing = true)
public class FakePaymentSimulatorController {

    /** {@code outcome}: APPROVED o REJECTED. */
    public record ResolveBody(@NotBlank String outcome) {
    }

    public record ResolveResult(String result) {
    }

    private final FakePaymentGateway gateway;
    private final PaymentRepositoryPort paymentRepository;
    private final OrderRepositoryPort orderRepository;
    private final ClientPort clientPort;
    private final ConfirmPaymentUseCase confirmPaymentUseCase;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/{providerTxId}/resolve")
    public ApiResponse<ResolveResult> resolve(@PathVariable String providerTxId,
                                              @Valid @RequestBody ResolveBody body) {
        UUID userId = currentUserResolver.requireCurrentUserId();
        Payment payment = paymentRepository.findByProviderAndProviderTxId(FakePaymentGateway.PROVIDER, providerTxId)
                .orElseThrow(() -> new OrderDomainException("Pago no encontrado"));
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new OrderDomainException("Pedido no encontrado"));
        UUID clientId = clientPort.findClientIdByUserId(userId).orElse(null);
        if (clientId == null || !clientId.equals(order.getClientId())) {
            throw new ForbiddenOperationException("Solo el cliente que hizo el pedido puede resolver su pago");
        }
        var notification = gateway.interpret(providerTxId, body.outcome());
        ConfirmPaymentUseCase.Outcome outcome = confirmPaymentUseCase.execute(
                FakePaymentGateway.PROVIDER, providerTxId, notification.approved(), notification.rawStatus());
        return ApiResponse.success(new ResolveResult(outcome.name()));
    }
}
