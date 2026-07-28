package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.ClaimDeliveryRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.ClaimDeliveryOrdersUseCase;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.exception.DeliveryPersonNotFoundException;
import com.flashdrop.delivery.domain.exception.RouteAlreadyAssignedException;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class ClaimDeliveryOrdersUseCaseImpl implements ClaimDeliveryOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClaimDeliveryOrdersUseCaseImpl.class);
    private static final int MAX_ORDERS_PER_CLAIM = 3;
    private static final BigDecimal DEFAULT_DISTANCE_KM = new BigDecimal("3.2");
    private static final Integer DEFAULT_ESTIMATED_MINUTES = 20;

    private final DeliveryPersonRepository deliveryPersonRepository;
    private final RouteRepository routeRepository;
    private final OrderServicePort orderServicePort;

    public ClaimDeliveryOrdersUseCaseImpl(DeliveryPersonRepository deliveryPersonRepository,
                                          RouteRepository routeRepository,
                                          OrderServicePort orderServicePort) {
        this.deliveryPersonRepository = deliveryPersonRepository;
        this.routeRepository = routeRepository;
        this.orderServicePort = orderServicePort;
    }

    @Override
    public List<DeliveryPersonResponse> execute(ClaimDeliveryRequest request) {
        log.info("Claiming delivery orders: {} for delivery person: {}",
                request.orderIds(), request.deliveryPersonId());

        DeliveryPerson deliveryPerson = deliveryPersonRepository.findById(request.deliveryPersonId())
                .orElseThrow(() -> new DeliveryPersonNotFoundException(request.deliveryPersonId()));

        List<OrderServicePort.OrderInfo> orders = orderServicePort.getOrdersByIds(request.orderIds());
        validateOrders(orders, request.orderIds());

        if (!orderServicePort.areOrdersFromSameRestaurant(request.orderIds())) {
            throw new IllegalArgumentException("All orders must be from the same restaurant");
        }

        for (OrderServicePort.OrderInfo order : orders) {
            DeliveryRoute route = createRouteForOrder(order);
            routeRepository.save(route);
        }

        log.info("Successfully claimed {} orders for delivery person {}",
                orders.size(), request.deliveryPersonId());

        return List.of(toDeliveryPersonResponse(deliveryPerson));
    }

    private void validateOrders(List<OrderServicePort.OrderInfo> orders, List<Long> requestedOrderIds) {
        if (orders.size() != requestedOrderIds.size()) {
            throw new IllegalArgumentException("Some orders were not found");
        }

        for (Long orderId : requestedOrderIds) {
            if (routeRepository.existsByOrderId(orderId)) {
                throw new RouteAlreadyAssignedException(orderId);
            }
        }
    }

    private DeliveryRoute createRouteForOrder(OrderServicePort.OrderInfo order) {
        return new DeliveryRoute(
                null,
                order.id(),
                order.pickupAddress(),
                order.deliveryAddress(),
                Distance.of(DEFAULT_DISTANCE_KM),
                EstimatedTime.of(DEFAULT_ESTIMATED_MINUTES),
                RouteStatus.PENDIENTE,
                Instant.now()
        );
    }

    private DeliveryPersonResponse toDeliveryPersonResponse(DeliveryPerson deliveryPerson) {
        return new DeliveryPersonResponse(
                deliveryPerson.getId(),
                deliveryPerson.getUserId(),
                deliveryPerson.getVehicle() != null ? deliveryPerson.getVehicle().name() : null,
                deliveryPerson.getCreatedAt()
        );
    }
}