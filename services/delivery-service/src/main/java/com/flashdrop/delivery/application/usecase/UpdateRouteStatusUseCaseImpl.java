package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.inbound.UpdateRouteStatusUseCase;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UpdateRouteStatusUseCaseImpl implements UpdateRouteStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateRouteStatusUseCaseImpl.class);

    private final RouteRepository routeRepository;

    public UpdateRouteStatusUseCaseImpl(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    @Override
    public RouteResponse execute(Long routeId, UpdateRouteStatusRequest request) {
        log.info("Updating route {} status to {}", routeId, request.status());

        RouteStatus newStatus;
        try {
            newStatus = RouteStatus.fromDbValue(request.status());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status: " + request.status());
        }

        DeliveryRoute updated = routeRepository.updateStatus(routeId, newStatus.getDbValue());
        log.info("Route {} status updated to {}", routeId, newStatus);

        return toRouteResponse(updated);
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