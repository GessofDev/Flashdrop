package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository;

import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SpringDataRefundRepository extends JpaRepository<RefundEntity, Long> {
    List<RefundEntity> findByOrderIdOrderByIdAsc(Long orderId);

    /** Todo reembolso cuenta (PENDIENTE, HECHO o FALLIDO): un FALLIDO se reintenta sobre la misma fila. */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundEntity r WHERE r.paymentId = :paymentId")
    BigDecimal sumAmountByPaymentId(@Param("paymentId") Long paymentId);
}
