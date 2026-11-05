package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository;

import cl.flashdrop.orders.domain.model.DeliveryOperationState;
import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderDeliveryOperationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataDeliveryOperationRepository extends JpaRepository<OrderDeliveryOperationEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderDeliveryOperationEntity o WHERE o.id = :id")
    Optional<OrderDeliveryOperationEntity> findByIdForUpdate(@Param("id") Long id);

    List<OrderDeliveryOperationEntity> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    @Query("SELECT o FROM OrderDeliveryOperationEntity o WHERE o.state = :#{#state.name()} ORDER BY o.createdAt ASC")
    List<OrderDeliveryOperationEntity> findByStateOrderByCreatedAtAsc(@Param("state") DeliveryOperationState state, Pageable pageable);
}
