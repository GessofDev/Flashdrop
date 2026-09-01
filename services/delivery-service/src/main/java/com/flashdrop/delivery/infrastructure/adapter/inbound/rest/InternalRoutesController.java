package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.dto.CreateDeliveryRouteRequest;
import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.outbound.RouteRepository;
import com.flashdrop.delivery.domain.model.DeliveryRoute;
import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;

import java.math.BigDecimal;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/routes")
public class InternalRoutesController {

    private static final Logger log = LoggerFactory.getLogger(InternalRoutesController.class);

    private final RouteRepository routeRepository;

    public InternalRoutesController(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RouteResponse>> createRoute(
            @Valid @RequestBody CreateDeliveryRouteRequest request) {
        log.info("POST /api/internal/routes - Creating route for orderId={}", request.orderId());

        DeliveryRoute route = new DeliveryRoute(
                null,
                request.orderId(),
                request.pickupAddress(),
                request.deliveryAddress(),
                Distance.of(request.distanceKm() != null
                        ? BigDecimal.valueOf(request.distanceKm())
                        : null),
                EstimatedTime.of(request.estimatedMinutes()),
                RouteStatus.ASSIGNED,
                null
        );

        DeliveryRoute saved = routeRepository.save(route);
        return new ResponseEntity<>(
                ApiResponse.success(toResponse(saved)),
                HttpStatus.CREATED);
    }

    @PatchMapping("/{routeId}/status")
    public ResponseEntity<ApiResponse<RouteResponse>> updateStatus(
            @PathVariable Long routeId,
            @Valid @RequestBody UpdateRouteStatusRequest request) {
        log.info("PATCH /api/internal/routes/{}/status - Updating status to: {}", routeId, request.status());

        DeliveryRoute updated = routeRepository.updateStatus(routeId, request.status());
        return ResponseEntity.ok(ApiResponse.success(toResponse(updated)));
    }

    /**
     * Updates a route's status by {@code orderId} (the only identifier orders-service
     * has when claiming an order). Resolves the underlying route via
     * {@link RouteRepository#findByOrderId(Long)} and delegates the actual status
     * transition to {@link RouteRepository#updateStatus(Long, String)}.
     *
     * <p>Returns 400 (via the {@link #handleIllegalArgument} handler) when no route
     * is found for the given {@code orderId}.
     */
    @PatchMapping("/order/{orderId}/status")
    public ResponseEntity<ApiResponse<RouteResponse>> updateStatusByOrderId(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateRouteStatusRequest request) {
        log.info("PATCH /api/internal/routes/order/{}/status - Updating status to: {}", orderId, request.status());

        DeliveryRoute route = routeRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No route found for orderId=" + orderId));
        DeliveryRoute updated = routeRepository.updateStatus(route.getId(), request.status());
        return ResponseEntity.ok(ApiResponse.success(toResponse(updated)));
    }

    private RouteResponse toResponse(DeliveryRoute route) {
        return new RouteResponse(
                route.getId(),
                route.getOrderId(),
                route.getPickupAddress(),
                route.getDeliveryAddress(),
                route.getDistanceKm() != null ? route.getDistanceKm().getValue().doubleValue() : null,
                route.getEstimatedMinutes() != null ? route.getEstimatedMinutes().getMinutes() : null,
                route.getStatus() != null ? route.getStatus().name() : null,
                route.getCreatedAt(),
                null
        );
    }

    /**
     * Handles illegal-argument payloads coming from the use case (e.g. a route id
     * that does not exist). Returns a clean 400 with the original message.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(java.lang.IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(java.lang.IllegalArgumentException ex) {
        log.warn("Bad request payload: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage()));
    }
}
