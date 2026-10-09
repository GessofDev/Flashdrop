package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.dto.CreateDeliveryPersonRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.CreateDeliveryPersonUseCase;
import com.flashdrop.delivery.infrastructure.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Courier self-signup endpoint (WU-4 of the openspec-feedback fix).
 *
 * <p>{@code POST /api/delivery/persons} — a user with role
 * {@code Repartidor} creates their own delivery profile. The
 * {@code SecurityConfig} matcher {@code hasRole("Repartidor")} guarantees
 * the caller has the right role; the controller then resolves the
 * {@code userId} from the JWT subject via {@link CurrentUserResolver}.
 * The body carries only the optional vehicle type.
 *
 * <p>This closes the original feedback loop: a courier with
 * {@code repartidor@demo.cl} can now create their profile on first login
 * without depending on the seed data being correct.
 */
@RestController
@RequestMapping(value = {"/api/delivery/persons", "/delivery/persons"})
public class DeliveryPersonController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryPersonController.class);

    private final CreateDeliveryPersonUseCase createDeliveryPersonUseCase;
    private final CurrentUserResolver currentUserResolver;

    public DeliveryPersonController(CreateDeliveryPersonUseCase createDeliveryPersonUseCase,
                                    CurrentUserResolver currentUserResolver) {
        this.createDeliveryPersonUseCase = createDeliveryPersonUseCase;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryPersonResponse>> createMyProfile(
            @Valid @RequestBody(required = false) CreateDeliveryPersonRequest request) {
        Long userId = currentUserResolver.requireCurrentUserId();
        if (userId == null) {
            // Defence in depth: SecurityConfig hasRole("Repartidor") should
            // have already enforced auth, but if it ever doesn't run we still
            // must not fall back to a default anonymous user.
            throw new AccessDeniedException("Authentication required");
        }
        CreateDeliveryPersonRequest body = request != null
                ? request
                : new CreateDeliveryPersonRequest(null);
        log.info("POST /api/delivery/persons - Signing up courier profile: userId={}, vehicle={}",
                userId, body.vehicle());
        DeliveryPersonResponse response = createDeliveryPersonUseCase.execute(userId, body);
        return new ResponseEntity<>(
                ApiResponse.success("Delivery person profile created", response),
                HttpStatus.CREATED);
    }
}
