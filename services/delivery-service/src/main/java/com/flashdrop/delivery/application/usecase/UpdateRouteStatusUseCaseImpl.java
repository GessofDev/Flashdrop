package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.inbound.UpdateRouteStatusUseCase;
import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UpdateRouteStatusUseCaseImpl implements UpdateRouteStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateRouteStatusUseCaseImpl.class);

    private final RouteRepository routeRepository;
    private final OrderServicePort orderServicePort;

    public UpdateRouteStatusUseCaseImpl(RouteRepository routeRepository,
                                        OrderServicePort orderServicePort) {
        this.routeRepository = routeRepository;
        this.orderServicePort = orderServicePort;
    }

    @Override
    public RouteResponse execute(Long routeId, UpdateRouteStatusRequest request) {
        log.info("Updating route {} status to {}", routeId, request.status());

        RouteStatus newStatus = RouteStatus.fromAnyValue(request.status());

        DeliveryRoute updated = routeRepository.updateStatus(routeId, newStatus.getDbValue());
        log.info("Route {} status updated to {}", routeId, newStatus);

        // Fetch code from orders table
        String code = fetchCodeForOrder(updated.getOrderId());

        return toRouteResponse(updated, code);
    }

    private String fetchCodeForOrder(Long orderId) {
        if (orderId == null) return null;
        List<OrderServicePort.OrderInfo> orders = orderServicePort.getOrdersByIds(List.of(orderId));
        return orders.isEmpty() ? null : orders.get(0).code();
    }

    private RouteResponse toRouteResponse(DeliveryRoute route, String code) {
        return new RouteResponse(
                route.getId(),
                route.getOrderId(),
                route.getPickupAddress(),
                route.getDeliveryAddress(),
                route.getDistanceKm().getValue().doubleValue(),
                route.getEstimatedMinutes().getMinutes(),
                route.getStatus() != null ? route.getStatus().name() : null,
                route.getCreatedAt(),
                code
        );
    }
}