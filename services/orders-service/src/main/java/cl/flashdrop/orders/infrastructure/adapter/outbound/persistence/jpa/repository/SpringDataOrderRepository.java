package cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.repository;

import cl.flashdrop.orders.infrastructure.adapter.outbound.persistence.jpa.entity.OrderEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByClientId(Long clientId);
    List<OrderEntity> findByRestaurantId(Long restaurantId);
    List<OrderEntity> findByRestaurantIdAndStatusNot(Long restaurantId, String status);
    Optional<OrderEntity> findByClientIdAndIdempotencyKey(Long clientId, String idempotencyKey);
    List<OrderEntity> findByDeliveryId(Long deliveryId);
    List<OrderEntity> findByIdIn(Collection<Long> ids);

    long countByClientIdAndStatusNotIn(Long clientId, Collection<String> statuses);

    long countByRestaurantIdAndStatusNotIn(Long restaurantId, Collection<String> statuses);

    long countByDeliveryIdAndStatusNotIn(Long deliveryId, Collection<String> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.address = '' WHERE o.clientId = :clientId AND o.address <> ''")
    int anonymizeAddressesByClient(@Param("clientId") Long clientId);

    /**
     * Lee el pedido con bloqueo de fila ({@code SELECT ... FOR UPDATE}): serializa los intentos
     * de código simultáneos sobre el mismo pedido para que nunca se superen los permitidos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);

    /**
     * Acepta el pedido: cambia de estado y registra la aceptacion por el comercio (F7).
     * La confirmacion del pago no utiliza esta operacion.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.status = :status, o.acceptedAt = :acceptedAt, o.courierDueAt = :courierDueAt "
            + "WHERE o.id = :id AND o.status = :expected")
    int acceptByStore(@Param("id") Long id, @Param("expected") String expected, @Param("status") String status,
                      @Param("acceptedAt") OffsetDateTime acceptedAt, @Param("courierDueAt") OffsetDateTime courierDueAt);

    /** Listo para retiro: queda fija la espera de repartidor; la hora de toma de la tienda se conserva (F7). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.status = :status, o.acceptedAt = COALESCE(o.acceptedAt, :now), "
            + "o.courierDueAt = :courierDueAt WHERE o.id = :id AND o.status = :expected")
    int markReadyForPickup(@Param("id") Long id, @Param("expected") String expected, @Param("status") String status,
                           @Param("now") OffsetDateTime now, @Param("courierDueAt") OffsetDateTime courierDueAt);

    @Query("SELECT o FROM OrderEntity o WHERE o.status IN :statuses AND o.deliveryId IS NULL "
            + "AND o.courierDueAt IS NOT NULL AND o.courierDueAt <= :now ORDER BY o.courierDueAt")
    List<OrderEntity> findCourierWaitOverdue(@Param("statuses") Collection<String> statuses,
                                            @Param("now") OffsetDateTime now, Pageable pageable);

    /** Cancela solo si sigue en el estado esperado y sin repartidor (F7). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.status = :cancelled, o.cancelReason = :reason, o.cancelledAt = :now "
            + "WHERE o.id = :id AND o.status = :expected AND o.deliveryId IS NULL")
    int cancelUnattended(@Param("id") Long id, @Param("expected") String expected, @Param("cancelled") String cancelled,
                         @Param("reason") String reason, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.courierDueAt = :due WHERE o.id = :id")
    int resetCourierDeadline(@Param("id") Long id, @Param("due") OffsetDateTime due);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.status = :status WHERE o.id = :id AND o.status = :expected")
    int updateStatusIfCurrent(@Param("id") Long id, @Param("expected") String expected,
                              @Param("status") String status);

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
