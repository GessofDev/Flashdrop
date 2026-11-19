package cl.flashdrop.orders.infrastructure.api;

import cl.flashdrop.orders.application.usecase.ConfirmPaymentUseCase;
import cl.flashdrop.orders.domain.port.PaymentGatewayPort;
import cl.flashdrop.orders.infrastructure.api.dto.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aviso de la pasarela con el resultado de un pago (F8). No lleva JWT de usuario: la pasarela se
 * identifica con la firma del aviso, que valida el adaptador de la pasarela. El aviso es idempotente.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    public record WebhookBody(@NotBlank String providerTxId, @NotBlank String status) {
    }

    public record WebhookResult(String result) {
    }

    private final PaymentGatewayPort paymentGateway;
    private final ConfirmPaymentUseCase confirmPaymentUseCase;

    @PostMapping("/webhook")
    public ApiResponse<WebhookResult> webhook(
            @RequestHeader(value = "X-Payment-Signature", required = false) String signature,
            @Valid @RequestBody WebhookBody body) {
        PaymentGatewayPort.PaymentNotification notification =
                paymentGateway.verifyNotification(body.providerTxId(), body.status(), signature);
        ConfirmPaymentUseCase.Outcome outcome = confirmPaymentUseCase.execute(
                paymentGateway.providerName(), notification.providerTxId(), notification.approved(),
                notification.rawStatus());
        return ApiResponse.success(new WebhookResult(outcome.name()));
    }
}
