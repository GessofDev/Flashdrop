package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository;

import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataPaymentRepository extends JpaRepository<PaymentEntity, Long> {
    List<PaymentEntity> findByOrderIdOrderByIdDesc(Long orderId);

    Optional<PaymentEntity> findByProviderAndProviderTxId(String provider, String providerTxId);
}
