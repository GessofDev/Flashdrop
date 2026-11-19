package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.dto.ReportCourierLocationRequest;
import com.flashdrop.delivery.application.dto.RouteLocationResponse;
import com.flashdrop.delivery.application.usecase.GetRouteLocationUseCase;
import com.flashdrop.delivery.application.usecase.ReportCourierLocationUseCase;
import com.flashdrop.delivery.infrastructure.security.CurrentUserResolver;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/delivery")
public class CourierLocationController {

    private final ReportCourierLocationUseCase reportCourierLocationUseCase;
    private final GetRouteLocationUseCase getRouteLocationUseCase;
    private final CurrentUserResolver currentUserResolver;

    public CourierLocationController(
            ReportCourierLocationUseCase reportCourierLocationUseCase,
            GetRouteLocationUseCase getRouteLocationUseCase,
            CurrentUserResolver currentUserResolver
    ) {
        this.reportCourierLocationUseCase = reportCourierLocationUseCase;
        this.getRouteLocationUseCase = getRouteLocationUseCase;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/location")
    public ResponseEntity<Void> reportLocation(@Valid @RequestBody ReportCourierLocationRequest request) {
        String userId = String.valueOf(currentUserResolver.requireCurrentUserId());
        reportCourierLocationUseCase.execute(
                userId,
                request.latitude(),
                request.longitude(),
                request.recordedAt()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/routes/{routeId}/location")
    public ResponseEntity<ApiResponse<RouteLocationResponse>> getRouteLocation(@PathVariable Long routeId) {
        String clientUserId = String.valueOf(currentUserResolver.requireCurrentUserId());
        RouteLocationResponse response = getRouteLocationUseCase.execute(clientUserId, routeId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
