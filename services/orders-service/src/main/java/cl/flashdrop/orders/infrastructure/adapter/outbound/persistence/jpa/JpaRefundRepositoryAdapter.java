package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa;

import cl.flashdrop.orders.domain.model.Refund;
import cl.flashdrop.orders.domain.model.RefundReason;
import cl.flashdrop.orders.domain.model.RefundStatus;
import cl.flashdrop.orders.domain.model.RefundType;
import cl.flashdrop.orders.domain.port.RefundRepositoryPort;
import cl.flashdrop.orders.infrastructure.adapter.outbound.IdConverter;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.RefundEntity;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataRefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile({"postgres", "default"})
@RequiredArgsConstructor
public class JpaRefundRepositoryAdapter implements RefundRepositoryPort {

    private final SpringDataRefundRepository repository;

    @Override
    @Transactional
    public Refund save(Refund refund) {
        OffsetDateTime now = OffsetDateTime.now();
        RefundEntity saved = repository.save(RefundEntity.builder()
                .id(refund.getId())
                .paymentId(refund.getPaymentId())
                .orderId(IdConverter.toLong(refund.getOrderId()))
                .refundType(refund.getType().name())
                .amount(refund.getAmount())
                .reason(refund.getReason().name())
                .status(refund.getStatus().name())
                .attempts(refund.getAttempts())
                .nextAttemptAt(refund.getNextAttemptAt())
                .refundKey(refund.getRefundKey())
                .providerRefundId(refund.getProviderRefundId())
                .lastError(refund.getLastError())
                .createdAt(refund.getCreatedAt() != null ? refund.getCreatedAt() : now)
                .updatedAt(refund.getUpdatedAt() != null ? refund.getUpdatedAt() : now)
                .build());
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Refund> findById(Long id) {
        return id == null ? Optional.empty() : repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Refund> findByOrderId(UUID orderId) {
        if (orderId == null) return List.of();
        return repository.findByOrderIdOrderByIdAsc(IdConverter.toLong(orderId)).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumCommittedByPaymentId(Long paymentId) {
        if (paymentId == null) return BigDecimal.ZERO;
        return repository.sumAmountByPaymentId(paymentId);
    }

    private Refund toDomain(RefundEntity e) {
        return Refund.builder()
                .id(e.getId())
                .paymentId(e.getPaymentId())
                .orderId(IdConverter.toUuid(e.getOrderId()))
                .type(RefundType.valueOf(e.getRefundType()))
                .amount(e.getAmount())
                .reason(RefundReason.valueOf(e.getReason()))
                .status(RefundStatus.valueOf(e.getStatus()))
                .attempts(e.getAttempts())
                .nextAttemptAt(e.getNextAttemptAt())
                .refundKey(e.getRefundKey())
                .providerRefundId(e.getProviderRefundId())
                .lastError(e.getLastError())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
