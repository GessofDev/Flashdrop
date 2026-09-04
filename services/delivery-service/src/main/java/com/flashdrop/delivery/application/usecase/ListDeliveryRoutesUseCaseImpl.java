package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ListDeliveryRoutesUseCaseImpl implements ListDeliveryRoutesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListDeliveryRoutesUseCaseImpl.class);

    private final RouteRepository routeRepository;
    private final OrderServicePort orderServicePort;

    public ListDeliveryRoutesUseCaseImpl(RouteRepository routeRepository,
                                         OrderServicePort orderServicePort) {
        this.routeRepository = routeRepository;
        this.orderServicePort = orderServicePort;
    }

    @Override
    public List<RouteResponse> execute(Long deliveryPersonId) {
        if (deliveryPersonId == null) {
            throw new IllegalArgumentException("deliveryPersonId is required");
        }

        List<DeliveryRoute> routes = routeRepository.findByDeliveryPersonId(deliveryPersonId);
        if (routes.isEmpty()) {
            return List.of();
        }

        // Batch-fetch order codes
        List<Long> orderIds = routes.stream()
                .map(DeliveryRoute::getOrderId)
                .collect(Collectors.toList());
        Map<Long, String> codeByOrderId = orderServicePort.getOrdersByIds(orderIds).stream()
                .collect(Collectors.toMap(
                        OrderServicePort.OrderInfo::id,
                        info -> info.code() != null ? info.code() : "",
                        (a, b) -> a));

        return routes.stream()
                .map(route -> toRouteResponse(route, codeByOrderId.getOrDefault(route.getOrderId(), null)))
                .toList();
    }

    private RouteResponse toRouteResponse(DeliveryRoute route, String code) {
        return new RouteResponse(
                route.getId(),
                route.getOrderId(),
                route.getPickupAddress(),
                route.getDeliveryAddress(),
                route.getDistanceKm().getValue().doubleValue(),
                route.getEstimatedMinutes().getMinutes(),
                route.getStatus() != null ? route.getStatus().getDbValue() : null,
                route.getCreatedAt(),
                code
        );
    }
}