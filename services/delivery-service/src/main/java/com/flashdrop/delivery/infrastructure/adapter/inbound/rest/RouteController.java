package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.inbound.UpdateRouteStatusUseCase;
import com.flashdrop.delivery.application.port.outbound.DeliveryPersonRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = {"/delivery", "/api/delivery"})
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final ListDeliveryRoutesUseCase listDeliveryRoutesUseCase;
    private final UpdateRouteStatusUseCase updateRouteStatusUseCase;
    private final DeliveryPersonRepository deliveryPersonRepository;

    public RouteController(ListDeliveryRoutesUseCase listDeliveryRoutesUseCase,
                           UpdateRouteStatusUseCase updateRouteStatusUseCase,
                           DeliveryPersonRepository deliveryPersonRepository) {
        this.listDeliveryRoutesUseCase = listDeliveryRoutesUseCase;
        this.updateRouteStatusUseCase = updateRouteStatusUseCase;
        this.deliveryPersonRepository = deliveryPersonRepository;
    }

    @GetMapping("/routes")
    public ResponseEntity<ApiResponse<List<RouteResponse>>> listRoutes() {
        Long userId = extractUserIdFromSecurityContext();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        Long deliveryPersonId = deliveryPersonRepository.findByUserId(Long.toString(userId))
                .map(dp -> dp.getId())
                .orElse(null);
        if (deliveryPersonId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        log.info("GET /delivery/routes - Listing routes for deliveryPersonId={}", deliveryPersonId);
        List<RouteResponse> routes = listDeliveryRoutesUseCase.execute(deliveryPersonId);
        return ResponseEntity.ok(ApiResponse.success("Routes retrieved successfully", routes));
    }

    @PutMapping("/routes/{routeId}/status")
    public ResponseEntity<ApiResponse<RouteResponse>> updateRouteStatus(
            @PathVariable Long routeId,
            @Valid @RequestBody UpdateRouteStatusRequest request) {
        log.info("PUT /delivery/routes/{}/status - Updating status to: {}", routeId, request.status());
        RouteResponse response = updateRouteStatusUseCase.execute(routeId, request);
        return ResponseEntity.ok(ApiResponse.success("Route status updated", response));
    }

    private Long extractUserIdFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            log.warn("Authenticated principal name is not a Long: {}", name);
            return null;
        }
    }
}