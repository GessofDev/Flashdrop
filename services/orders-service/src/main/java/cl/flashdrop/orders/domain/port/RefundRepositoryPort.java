package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.Refund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para el libro de reembolsos. */
public interface RefundRepositoryPort {

    Refund save(Refund refund);

    Optional<Refund> findById(Long id);

    List<Refund> findByOrderId(UUID orderId);

    /**
     * Suma de lo devuelto o en camino de devolverse para ese pago (reembolsos PENDIENTE, HECHO
     * y FALLIDO que aun se reintentan). Es el {@code alreadyRefunded} de {@code Refund.open}.
     */
    BigDecimal sumCommittedByPaymentId(Long paymentId);
}
