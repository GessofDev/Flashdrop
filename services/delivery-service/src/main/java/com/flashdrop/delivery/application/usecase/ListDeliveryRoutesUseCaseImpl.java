package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListDeliveryRoutesUseCaseImpl implements ListDeliveryRoutesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListDeliveryRoutesUseCaseImpl.class);

    private final RouteRepository routeRepository;

    public ListDeliveryRoutesUseCaseImpl(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    @Override
    public List<RouteResponse> execute(Long deliveryPersonId) {
        // delivery_routes table does not have a delivery_person_id column,
        // so we list all routes. The param is preserved for API contract compatibility.
        log.info("Listing delivery routes (deliveryPersonId param={} ignored)", deliveryPersonId);

        List<DeliveryRoute> routes = routeRepository.findAll();

        return routes.stream()
                .map(this::toRouteResponse)
                .toList();
    }

    private RouteResponse toRouteResponse(DeliveryRoute route) {
        return new RouteResponse(
                route.getId(),
                route.getOrderId(),
                route.getPickupAddress(),
                route.getDeliveryAddress(),
                route.getDistanceKm().getValue().doubleValue(),
                route.getEstimatedMinutes().getMinutes(),
                route.getStatus() != null ? route.getStatus().name() : null,
                route.getCreatedAt()
        );
    }
}