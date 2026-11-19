package cl.flashdrop.orders.infrastructure.adapter.outbound.payment;

import cl.flashdrop.orders.domain.exception.ForbiddenOperationException;
import cl.flashdrop.orders.domain.exception.OrderDomainException;
import cl.flashdrop.orders.domain.port.PaymentGatewayPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Pasarela de pago simulada (F8). No cobra nada: abre un cobro con un identificador propio y deja que
 * la app resuelva el resultado (aprobado o rechazado) desde su pantalla de pago simulada.
 *
 * <p>Se activa con {@code orders.payment.provider=fake} (valor por defecto). Para la pasarela real se
 * crea otro adaptador de {@link PaymentGatewayPort} y se cambia la propiedad.</p>
 *
 * <p>El aviso por webhook se firma con un secreto compartido ({@code orders.payment.webhook-secret}).
 * Sin secreto configurado se rechaza todo aviso (falla cerrado), igual que la clave interna.</p>
 */
@Component
@ConditionalOnProperty(name = "orders.payment.provider", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGateway implements PaymentGatewayPort {

    public static final String PROVIDER = "FAKE";
    static final Set<String> APPROVED = Set.of("APPROVED", "APROBADO");
    static final Set<String> REJECTED = Set.of("REJECTED", "RECHAZADO", "DECLINED");

    private final byte[] webhookSecret;

    public FakePaymentGateway(@Value("${orders.payment.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret == null ? new byte[0] : webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public PaymentSession createSession(UUID orderId, BigDecimal amount, String currency) {
        String txId = "fake-" + UUID.randomUUID();
        return new PaymentSession(txId, paymentUrlFor(txId));
    }

    @Override
    public String paymentUrlFor(String providerTxId) {
        return "fake://flashdrop-pay/" + providerTxId;
    }

    @Override
    public PaymentNotification verifyNotification(String providerTxId, String status, String signature) {
        if (webhookSecret.length == 0 || signature == null
                || !MessageDigest.isEqual(webhookSecret, signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenOperationException("Firma del aviso de pago invalida");
        }
        return interpret(providerTxId, status);
    }

    /** Traduce el estado que reporta la pasarela a un resultado (sin validar firma). */
    public PaymentNotification interpret(String providerTxId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (APPROVED.contains(normalized)) {
            return new PaymentNotification(providerTxId, true, normalized);
        }
        if (REJECTED.contains(normalized)) {
            return new PaymentNotification(providerTxId, false, normalized);
        }
        throw new OrderDomainException("Estado de pago no reconocido: " + status);
    }
}
