package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository;

import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataOrderPolicyRepository extends JpaRepository<OrderPolicyEntity, Long> {
    Optional<OrderPolicyEntity> findByRestaurantId(Long restaurantId);

    Optional<OrderPolicyEntity> findByRestaurantIdIsNull();
}
