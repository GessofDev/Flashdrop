package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.Payment;
import cl.flashdrop.orders.domain.model.PaymentStatus;
import cl.flashdrop.orders.domain.port.PaymentRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.PaymentEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile({"postgres", "default"})
@RequiredArgsConstructor
public class JpaPaymentRepositoryAdapter implements PaymentRepositoryPort {

    private final SpringDataPaymentRepository repository;

    @Override
    @Transactional
    public Payment save(Payment payment) {
        OffsetDateTime now = OffsetDateTime.now();
        PaymentEntity saved = repository.save(PaymentEntity.builder()
                .id(payment.getId())
                .orderId(IdConverter.toLong(payment.getOrderId()))
                .provider(payment.getProvider())
                .providerTxId(payment.getProviderTxId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .rawStatus(payment.getRawStatus())
                .createdAt(payment.getCreatedAt() != null ? payment.getCreatedAt() : now)
                .updatedAt(payment.getUpdatedAt() != null ? payment.getUpdatedAt() : now)
                .build());
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findById(Long id) {
        return id == null ? Optional.empty() : repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findByOrderId(UUID orderId) {
        if (orderId == null) return List.of();
        return repository.findByOrderIdOrderByIdDesc(IdConverter.toLong(orderId)).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByProviderAndProviderTxId(String provider, String providerTxId) {
        if (provider == null || providerTxId == null) return Optional.empty();
        return repository.findByProviderAndProviderTxId(provider, providerTxId).map(this::toDomain);
    }

    private Payment toDomain(PaymentEntity e) {
        return Payment.builder()
                .id(e.getId())
                .orderId(IdConverter.toUuid(e.getOrderId()))
                .provider(e.getProvider())
                .providerTxId(e.getProviderTxId())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .status(PaymentStatus.valueOf(e.getStatus()))
                .rawStatus(e.getRawStatus())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
