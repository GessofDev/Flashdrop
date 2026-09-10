package com.flashdrop.delivery.application.port.outbound;

import com.flashdrop.delivery.domain.model.DeliveryRoute;

import java.util.List;
import java.util.Optional;

public interface RouteRepository {

    Optional<DeliveryRoute> findById(Long id);

    Optional<DeliveryRoute> findByOrderId(Long orderId);

    List<DeliveryRoute> findAll();

    List<DeliveryRoute> findByDeliveryPersonId(Long deliveryPersonId);

    DeliveryRoute save(DeliveryRoute route);

    boolean existsByOrderId(Long orderId);

    DeliveryRoute updateStatus(Long id, String status);

    /**
     * Plan §9.5 D8: assigns an existing pre-created route (created upstream
     * by Orders via {@code POST /api/internal/routes}, C-6) to a delivery
     * person. The implementation is required to:
     *
     * <ul>
     *   <li>throw {@link com.flashdrop.delivery.domain.exception.RouteNotPrecreatedException}
     *       when no route exists for the given {@code orderId};</li>
     *   <li>throw {@link com.flashdrop.delivery.domain.exception.RouteAlreadyAssignedException}
     *       when the route already has a {@code deliveryPersonId};</li>
     *   <li>acquire a row-level lock (e.g. {@code SELECT … FOR UPDATE}) so two
     *       concurrent claims cannot both win the same {@code orderId};</li>
     *   <li>transition {@code status} from {@code PENDIENTE} to {@code ASSIGNED}.</li>
     * </ul>
     */
    DeliveryRoute assignDeliveryPerson(Long orderId, Long deliveryPersonId);
}