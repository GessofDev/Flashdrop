package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.dto.RouteResponse;
import com.flashdrop.delivery.application.dto.UpdateRouteStatusRequest;
import com.flashdrop.delivery.application.port.inbound.ListDeliveryRoutesUseCase;
import com.flashdrop.delivery.application.port.inbound.UpdateRouteStatusUseCase;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = {"/delivery", "/api/delivery"})
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final ListDeliveryRoutesUseCase listDeliveryRoutesUseCase;
    private final UpdateRouteStatusUseCase updateRouteStatusUseCase;

    public RouteController(ListDeliveryRoutesUseCase listDeliveryRoutesUseCase,
                           UpdateRouteStatusUseCase updateRouteStatusUseCase) {
        this.listDeliveryRoutesUseCase = listDeliveryRoutesUseCase;
        this.updateRouteStatusUseCase = updateRouteStatusUseCase;
    }

    @GetMapping("/routes")
    public ResponseEntity<ApiResponse<List<RouteResponse>>> listRoutes(
            @RequestParam(required = false) Long deliveryPersonId) {
        log.info("GET /delivery/routes - Listing routes (deliveryPersonId={}, ignored: no column in DB)", deliveryPersonId);
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
}