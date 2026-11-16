package cl.flashdrop.orders.domain.port;

import cl.flashdrop.orders.domain.model.Payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para el libro de pagos. */
public interface PaymentRepositoryPort {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    /** Intentos de cobro del pedido, el mas reciente primero. */
    List<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByProviderAndProviderTxId(String provider, String providerTxId);
}
