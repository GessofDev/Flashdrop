package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository;

import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderDeliveryCodeAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataOrderDeliveryCodeAttemptRepository
        extends JpaRepository<OrderDeliveryCodeAttemptEntity, Long> {
    long countByOrderId(Long orderId);
}
