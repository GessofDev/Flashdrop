package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.dto.ClaimDeliveryRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.ClaimDeliveryOrdersUseCase;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = {"/delivery", "/api/delivery"})
public class DeliveryController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryController.class);

    private final ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase;

    public DeliveryController(ClaimDeliveryOrdersUseCase claimDeliveryOrdersUseCase) {
        this.claimDeliveryOrdersUseCase = claimDeliveryOrdersUseCase;
    }

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<List<DeliveryPersonResponse>>> claimDelivery(
            @Valid @RequestBody ClaimDeliveryRequest request) {
        // Actor identity comes from the JWT subject, NOT from the body. Closes the IDOR.
        Long userId = extractUserIdFromSecurityContext();
        if (userId == null) {
            // Defence in depth: the security filter should already have rejected
            // unauthenticated requests, but if it ever doesn't run we still must
            // not fall back to a default anonymous user.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Authentication required"));
        }
        log.info("POST /delivery/claim - Claiming delivery orders: {} for userId={}",
                request.orderIds(), userId);
        List<DeliveryPersonResponse> response = claimDeliveryOrdersUseCase.execute(userId, request);
        return new ResponseEntity<>(
                ApiResponse.success("Delivery claimed successfully", response),
                HttpStatus.CREATED);
    }

    /**
     * Extract the courier's userId from {@link SecurityContextHolder}. Returns
     * {@code null} if no authenticated principal is present — the controller
     * maps this to a 401 (defence-in-depth: the {@code JwtAuthenticationFilter}
     * should have already enforced auth, but if it ever doesn't run, we still
     * must not fall back to a default anonymous user).
     */
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