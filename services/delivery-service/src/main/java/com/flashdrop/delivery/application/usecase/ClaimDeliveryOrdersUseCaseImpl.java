package com.flashdrop.delivery.application.usecase;

import com.flashdrop.delivery.application.dto.ClaimDeliveryRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.ClaimDeliveryOrdersUseCase;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import com.flashdrop.delivery.application.port.outbound.InternalOrdersClientPort;
import com.flashdrop.delivery.application.port.outbound.OrderServicePort;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.exception.DeliveryPersonNotFoundException;
import com.flashdrop.delivery.domain.exception.OrderClaimFailedException;
import com.flashdrop.delivery.domain.exception.RouteAlreadyAssignedException;
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class ClaimDeliveryOrdersUseCaseImpl implements ClaimDeliveryOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClaimDeliveryOrdersUseCaseImpl.class);
    private static final BigDecimal DEFAULT_DISTANCE_KM = new BigDecimal("3.2");
    private static final Integer DEFAULT_ESTIMATED_MINUTES = 20;

    private final DeliveryPersonRepository deliveryPersonRepository;
    private final RouteRepository routeRepository;
    private final OrderServicePort orderServicePort;
    private final InternalOrdersClientPort internalOrdersClient;
    private final boolean delegateToOrdersEnabled;

    public ClaimDeliveryOrdersUseCaseImpl(DeliveryPersonRepository deliveryPersonRepository,
                                          RouteRepository routeRepository,
                                          OrderServicePort orderServicePort,
                                          InternalOrdersClientPort internalOrdersClient,
                                          @Value("${delivery.claim.delegate-to-orders.enabled:false}") boolean delegateToOrdersEnabled) {
        this.deliveryPersonRepository = deliveryPersonRepository;
        this.routeRepository = routeRepository;
        this.orderServicePort = orderServicePort;
        this.internalOrdersClient = internalOrdersClient;
        this.delegateToOrdersEnabled = delegateToOrdersEnabled;
    }

    @Override
    public List<DeliveryPersonResponse> execute(Long userId, ClaimDeliveryRequest request) {
        // Resolve the courier from the JWT subject (no longer from the body — IDOR fix).
        DeliveryPerson deliveryPerson = deliveryPersonRepository
                .findByUserId(Long.toString(userId))
                .orElseThrow(() -> new DeliveryPersonNotFoundException(userId));

        List<OrderServicePort.OrderInfo> orders = orderServicePort.getOrdersByIds(request.orderIds());
        validateOrders(orders, request.orderIds());

        if (!orderServicePort.areOrdersFromSameRestaurant(request.orderIds())) {
            throw new IllegalArgumentException("All orders must be from the same restaurant");
        }

        for (OrderServicePort.OrderInfo order : orders) {
            DeliveryRoute route = createRouteForOrder(order);
            routeRepository.save(route);
        }

        // -------------------------------------------------------------------
        // PR-B: post-save hook — delegate to orders-service so it can update
        // orders.delivery_id / orders.status. Feature-flagged (D8: defaults
        // OFF, production behavior unchanged until the flag is flipped).
        //
        // Wire contract (D5):
        //   POST {orders.service.url}/api/internal/orders/claim
        //   Headers: X-Internal-Api-Key: ${internal.api.key}
        //   Body:    { "userId": <long>, "orderIds": [<long>, ...] }
        //
        // Orphan-route semantics: the routes above are already persisted by
        // the time we get here. If the orders call fails, those routes are
        // orphan (delivery has them, orders doesn't reflect them). This is
        // STRICTLY BETTER than today's behavior — today, ALL routes are
        // orphan; with PR-B, only flag-ON-and-failed-upstream routes are.
        // A future reconciliation job will close the gap (plan §Out of scope).
        //
        // The OrderClaimFailedException is intentionally re-thrown: the global
        // exception handler maps it to a structured 5xx response so the
        // courier knows the claim partially failed.
        // -------------------------------------------------------------------
        if (delegateToOrdersEnabled) {
            List<Long> uniqueOrderIds = request.orderIds().stream().distinct().toList();
            try {
                internalOrdersClient.claimOrders(userId, uniqueOrderIds);
            } catch (OrderClaimFailedException ex) {
                log.error("Orders claim delegation failed for userId={}, orderIds={}, upstreamStatus={}: {}",
                        userId, uniqueOrderIds, ex.getStatus(), ex.getMessage(), ex);
                throw ex;
            }
        }

        log.info("Successfully claimed {} orders for delivery person (userId={})",
                orders.size(), userId);

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