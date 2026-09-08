package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository;

import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByClientId(Long clientId);
    List<OrderEntity> findByRestaurantId(Long restaurantId);
    List<OrderEntity> findByDeliveryId(Long deliveryId);
    List<OrderEntity> findByIdIn(Collection<Long> ids);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.deliveryId = :deliveryId AND o.status IN :statuses")
    long countByDeliveryIdAndStatusIn(@Param("deliveryId") Long deliveryId, @Param("statuses") Collection<String> statuses);
}
