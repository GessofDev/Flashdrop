package com.flashdrop.delivery.application.port.inbound;

import com.flashdrop.delivery.application.dto.ClaimDeliveryRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;

import java.util.List;

public interface ClaimDeliveryOrdersUseCase {

    /**
     * Claim the given orders on behalf of the courier identified by
     * {@code userId}. The {@code userId} is the JWT subject
     * ({@code Long.toString} of the auth-service {@code userId} claim),
     * extracted by the controller from {@code SecurityContextHolder}.
     *
     * <p>Keeping the use case Spring-context-free makes it testable without
     * the security filter chain — the controller is the only layer that
     * knows about Spring Security.
     */
    List<DeliveryPersonResponse> execute(Long userId, ClaimDeliveryRequest request);
}