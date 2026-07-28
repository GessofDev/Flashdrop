package com.flashdrop.delivery.application.port.outbound;

import com.flashdrop.delivery.domain.model.DeliveryRoute;

import java.util.List;
import java.util.Optional;

public interface RouteRepository {

    Optional<DeliveryRoute> findById(Long id);

    Optional<DeliveryRoute> findByOrderId(Long orderId);

    List<DeliveryRoute> findAll();

    DeliveryRoute save(DeliveryRoute route);

    boolean existsByOrderId(Long orderId);

    DeliveryRoute updateStatus(Long id, String status);
}