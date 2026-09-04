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
import com.flashdrop.delivery.domain.model.DeliveryPerson;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ClaimDeliveryOrdersUseCaseImpl implements ClaimDeliveryOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClaimDeliveryOrdersUseCaseImpl.class);

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

    /**
     * Plan §9.5 D8 — claim is now UPDATE-based: looks up the route pre-created
     * by Orders (C-6: {@code POST /api/internal/routes}) and binds the
     * courier. The whole claim batch runs in a single transaction so a 409 on
     * one route rolls back every other assignment.
     */
    @Override
    @Transactional
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

        List<DeliveryRoute> claimedRoutes = new ArrayList<>(orders.size());
        for (OrderServicePort.OrderInfo order : orders) {
            // Throws RouteNotPrecreatedException (Orders hasn't published the
            // route yet) or RouteAlreadyAssignedException (another courier
            // won the race). Both extend RuntimeException — Spring's default
            // rollback rules trigger on RuntimeException, so the transaction
            // unwinds and no partial claim persists.
            DeliveryRoute claimed = routeRepository.assignDeliveryPerson(
                    order.id(), deliveryPerson.getId());
            claimedRoutes.add(claimed);
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
        // Orphan-route semantics: the routes above are already mutated by
        // the time we get here (UPDATE, not INSERT — they're already owned
        // by Orders). If the orders call fails, those updates are NOT
        // orphan — delivery's row is internally consistent. What is missing
        // is the orders-side state: orders doesn't know who claimed what.
        // A future reconciliation job will close the gap (plan §Out of scope).
        //
        // The OrderClaimFailedException is intentionally re-thrown: the global
        // exception handler maps it to a structured 5xx response so the
        // courier knows the claim partially failed. Because we are inside a
        // @Transactional method, the runtime exception causes Spring to roll
        // back the route assignments too.
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
                claimedRoutes.size(), userId);

        return List.of(toDeliveryPersonResponse(deliveryPerson));
    }

    private void validateOrders(List<OrderServicePort.OrderInfo> orders, List<Long> requestedOrderIds) {
        if (orders.size() != requestedOrderIds.size()) {
            throw new IllegalArgumentException("Some orders were not found");
        }
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
