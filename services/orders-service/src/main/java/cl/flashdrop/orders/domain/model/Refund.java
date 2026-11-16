package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Devolucion de dinero de un pago (FlashDrop 1.0 tiendas, spec 4.1).
 *
 * <p>Puede ser TOTAL o PARCIAL. La regla "la suma de lo devuelto nunca supera lo cobrado" vive
 * en {@link #open}, porque depende del monto del pago (otra tabla) y la base no puede
 * verificarla sola.</p>
 */
@Getter
@Builder
public class Refund {

    /** Largo maximo de {@code refunds.last_error}. */
    static final int MAX_ERROR_LENGTH = 500;

    private final Long id;
    private final Long paymentId;
    private final UUID orderId;
    private final RefundType type;
    private final BigDecimal amount;
    private final RefundReason reason;
    @Builder.Default
    private RefundStatus status = RefundStatus.PENDIENTE;
    @Builder.Default
    private int attempts = 0;
    private OffsetDateTime nextAttemptAt;
    /** Llave de idempotencia enviada a la pasarela: reintentar nunca devuelve dos veces. */
    private final String refundKey;
    private String providerRefundId;
    private String lastError;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * Abre un reembolso sobre un pago aprobado.
     *
     * @param alreadyRefunded lo ya devuelto o en proceso de devolverse para ese pago
     *                        (reembolsos PENDIENTE o HECHO; los FALLIDO que se reintentan
     *                        se cuentan tambien, porque reutilizan su fila)
     * @param amount          monto a devolver; para TOTAL debe ser todo lo cobrado
     */
    public static Refund open(Payment payment, RefundType type, BigDecimal amount, RefundReason reason,
                              BigDecimal alreadyRefunded, String refundKey, OffsetDateTime now) {
        if (payment == null || payment.getId() == null) {
            throw new OrderDomainException("El reembolso requiere un pago registrado");
        }
        if (!payment.isApproved()) {
            throw new OrderDomainException("Solo se puede reembolsar un pago aprobado");
        }
        if (type == null || reason == null) {
            throw new OrderDomainException("El reembolso requiere tipo y motivo");
        }
        if (refundKey == null || refundKey.isBlank()) {
            throw new OrderDomainException("El reembolso requiere una llave de idempotencia");
        }
        BigDecimal already = alreadyRefunded == null ? BigDecimal.ZERO : alreadyRefunded;
        if (amount == null || amount.signum() <= 0) {
            throw new OrderDomainException("El monto a reembolsar debe ser mayor a 0");
        }
        BigDecimal remaining = payment.getAmount().subtract(already);
        if (type == RefundType.TOTAL) {
            if (already.signum() != 0 || amount.compareTo(payment.getAmount()) != 0) {
                throw new OrderDomainException(
                        "Un reembolso total debe devolver todo lo cobrado y no puede haber otros previos");
            }
        } else if (amount.compareTo(remaining) > 0) {
            throw new OrderDomainException("El reembolso supera lo que queda por devolver (" + remaining + ")");
        }
        return Refund.builder()
                .paymentId(payment.getId()).orderId(payment.getOrderId())
                .type(type).amount(amount).reason(reason).refundKey(refundKey)
                .status(RefundStatus.PENDIENTE).attempts(0)
                .createdAt(now).updatedAt(now)
                .build();
    }

    public void succeed(String providerRefundId, OffsetDateTime now) {
        if (status == RefundStatus.HECHO) {
            return;
        }
        this.status = RefundStatus.HECHO;
        this.providerRefundId = providerRefundId;
        this.lastError = null;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    /** Registra un intento fallido y cuando se reintentara. Un reembolso HECHO no se toca. */
    public void fail(String error, OffsetDateTime nextAttemptAt, OffsetDateTime now) {
        if (status == RefundStatus.HECHO) {
            throw new OrderDomainException("El reembolso ya se completo");
        }
        this.status = RefundStatus.FALLIDO;
        this.attempts++;
        this.lastError = error == null ? null
                : (error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error);
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = now;
    }
}
