package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository;

import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByClientId(Long clientId);
    List<OrderEntity> findByRestaurantId(Long restaurantId);
    List<OrderEntity> findByDeliveryId(Long deliveryId);
    List<OrderEntity> findByIdIn(Collection<Long> ids);

    /**
     * Asignación atómica: asigna el repartidor SOLO a los pedidos que siguen sin repartidor,
     * en una única sentencia. Devuelve cuántos pedidos se asignaron; si dos repartidores
     * compiten por el mismo pedido, la base de datos deja pasar solo a uno.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.deliveryId = :deliveryId WHERE o.id IN :ids AND o.deliveryId IS NULL")
    int claimUnassigned(@Param("deliveryId") Long deliveryId, @Param("ids") Collection<Long> ids);

    List<OrderEntity> findByRestaurantIdAndStatusAndDeliveryIdIsNullOrderByCreatedAtAsc(
            Long restaurantId, String status, Pageable pageable);

    List<OrderEntity> findByRestaurantIdAndStatusInAndCreatedAtBetween(
            Long restaurantId, Collection<String> statuses, OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.deliveryId = :deliveryId AND o.status IN :statuses")
    long countByDeliveryIdAndStatusIn(@Param("deliveryId") Long deliveryId, @Param("statuses") Collection<String> statuses);
}
