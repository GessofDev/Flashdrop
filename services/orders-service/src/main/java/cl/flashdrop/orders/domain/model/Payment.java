package cl.flashdrop.orders.domain.model;

import cl.flashdrop.orders.domain.exception.OrderDomainException;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un intento de cobro de un pedido (FlashDrop 1.0 tiendas, spec 4.1).
 *
 * <p>Sin anotaciones de framework, igual que {@link Order}. El monto lo calcula el servidor:
 * nunca viene del cliente. Un pedido puede tener varios intentos, pero la base de datos impide
 * dos abiertos o dos aprobados a la vez.</p>
 */
@Getter
@Builder
public class Payment {

    private final Long id;
    private final UUID orderId;
    private final String provider;
    private final String providerTxId;
    private final BigDecimal amount;
    @Builder.Default
    private final String currency = "CLP";
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDIENTE;
    private String rawStatus;
    private final OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Crea un cobro nuevo en estado PENDIENTE validando lo que la base tambien exige. */
    public static Payment open(UUID orderId, String provider, String providerTxId, BigDecimal amount,
                               OffsetDateTime now) {
        if (orderId == null) throw new OrderDomainException("El pago debe pertenecer a un pedido");
        if (provider == null || provider.isBlank()) throw new OrderDomainException("El pago requiere proveedor");
        if (providerTxId == null || providerTxId.isBlank()) {
            throw new OrderDomainException("El pago requiere el identificador de la transaccion");
        }
        if (amount == null || amount.signum() <= 0) throw new OrderDomainException("El monto del pago debe ser mayor a 0");
        return Payment.builder()
                .orderId(orderId).provider(provider).providerTxId(providerTxId).amount(amount)
                .status(PaymentStatus.PENDIENTE).createdAt(now).updatedAt(now)
                .build();
    }

    public boolean isApproved() {
        return status == PaymentStatus.APROBADO;
    }

    public void approve(String rawStatus, OffsetDateTime now) {
        moveTo(PaymentStatus.APROBADO, rawStatus, now);
    }

    public void reject(String rawStatus, OffsetDateTime now) {
        moveTo(PaymentStatus.RECHAZADO, rawStatus, now);
    }

    public void expire(String rawStatus, OffsetDateTime now) {
        moveTo(PaymentStatus.EXPIRADO, rawStatus, now);
    }

    /**
     * Solo un pago PENDIENTE puede resolverse. Repetir el mismo resultado es valido (los
     * webhooks se reenvian: debe ser idempotente); cambiar un resultado ya final no lo es.
     */
    private void moveTo(PaymentStatus target, String rawStatus, OffsetDateTime now) {
        if (status == target) {
            return;
        }
        if (status != PaymentStatus.PENDIENTE) {
            throw new OrderDomainException("El pago ya fue resuelto como " + status
                    + " y no puede pasar a " + target);
        }
        this.status = target;
        this.rawStatus = rawStatus;
        this.updatedAt = now;
    }
}
