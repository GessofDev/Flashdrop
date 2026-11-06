package com.flashdrop.delivery.infrastructure.adapter.inbound.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.delivery.application.dto.AdminCreateDeliveryPersonRequest;
import com.flashdrop.delivery.application.dto.ApiResponse;
import com.flashdrop.delivery.application.dto.CreateDeliveryPersonRequest;
import com.flashdrop.delivery.application.dto.DeliveryPersonResponse;
import com.flashdrop.delivery.application.port.inbound.CreateDeliveryPersonUseCase;
import com.flashdrop.delivery.infrastructure.security.CurrentUserResolver;

import jakarta.validation.Valid;

/**
 * Alta de repartidores (F6). Solo administradores: el usuario con los roles Cliente, Restaurante y
 * Repartidor a la vez (regla en {@code SecurityConfig}; no existe un rol "Admin" aparte). El usuario
 * debe tener ya el rol Repartidor, que se asigna antes en auth-service.
 */
@RestController
@RequestMapping("/api/delivery/admin/persons")
public class AdminDeliveryPersonController {

    private static final Logger log = LoggerFactory.getLogger(AdminDeliveryPersonController.class);

    private final CreateDeliveryPersonUseCase createDeliveryPersonUseCase;
    private final CurrentUserResolver currentUserResolver;

    public AdminDeliveryPersonController(CreateDeliveryPersonUseCase createDeliveryPersonUseCase,
                                         CurrentUserResolver currentUserResolver) {
        this.createDeliveryPersonUseCase = createDeliveryPersonUseCase;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryPersonResponse>> create(
            @Valid @RequestBody AdminCreateDeliveryPersonRequest request) {
        DeliveryPersonResponse created = createDeliveryPersonUseCase.execute(
                request.userId(), new CreateDeliveryPersonRequest(request.vehicle()));
        log.info("ADMIN_CREATE_DELIVERY_PERSON actor={} userId={}",
                currentUserResolver.requireCurrentUserId(), request.userId());
        return new ResponseEntity<>(ApiResponse.success("Delivery person profile created", created),
                HttpStatus.CREATED);
    }
}
