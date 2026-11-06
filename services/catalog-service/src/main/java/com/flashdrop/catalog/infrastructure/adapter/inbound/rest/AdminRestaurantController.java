package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.catalog.application.usecase.AssignRestaurantOwnerUseCase;
import com.flashdrop.catalog.application.usecase.CreateRestaurantUseCase;
import com.flashdrop.catalog.application.usecase.ListRestaurantsUseCase;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.AdminRestaurantResponse;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.AssignRestaurantOwnerRequest;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.CreateRestaurantRequest;

import jakarta.validation.Valid;

/**
 * Alta de tiendas (F6). Solo administradores: el usuario con los roles Cliente, Restaurante y
 * Repartidor a la vez (regla en {@code SecurityConfig}). No existe un rol "Admin" aparte.
 */
@RestController
@RequestMapping("/api/catalog/admin/restaurants")
public class AdminRestaurantController {

    private static final Logger log = LoggerFactory.getLogger(AdminRestaurantController.class);

    private final CreateRestaurantUseCase createRestaurantUseCase;
    private final AssignRestaurantOwnerUseCase assignRestaurantOwnerUseCase;
    private final ListRestaurantsUseCase listRestaurantsUseCase;
    private final Clock clock;

    public AdminRestaurantController(
            CreateRestaurantUseCase createRestaurantUseCase,
            AssignRestaurantOwnerUseCase assignRestaurantOwnerUseCase,
            ListRestaurantsUseCase listRestaurantsUseCase,
            Clock clock
    ) {
        this.createRestaurantUseCase = createRestaurantUseCase;
        this.assignRestaurantOwnerUseCase = assignRestaurantOwnerUseCase;
        this.listRestaurantsUseCase = listRestaurantsUseCase;
        this.clock = clock;
    }

    @GetMapping
    public List<AdminRestaurantResponse> list() {
        LocalDateTime now = LocalDateTime.now(clock);
        return listRestaurantsUseCase.execute().stream()
                .map(restaurant -> AdminRestaurantResponse.fromDomain(restaurant, now))
                .toList();
    }

    @PostMapping
    public ResponseEntity<AdminRestaurantResponse> create(
            @AuthenticationPrincipal Jwt admin,
            @Valid @RequestBody CreateRestaurantRequest request
    ) {
        var restaurant = createRestaurantUseCase.execute(request.name(), request.address(), request.ownerUserId());
        log.info("ADMIN_CREATE_RESTAURANT actor={} restaurantId={} ownerUserId={}",
                actor(admin), restaurant.getId(), restaurant.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminRestaurantResponse.fromDomain(restaurant, LocalDateTime.now(clock)));
    }

    @PutMapping("/{restaurantId}/owner")
    public AdminRestaurantResponse assignOwner(
            @AuthenticationPrincipal Jwt admin,
            @PathVariable Long restaurantId,
            @Valid @RequestBody AssignRestaurantOwnerRequest request
    ) {
        var restaurant = assignRestaurantOwnerUseCase.execute(restaurantId, request.userId());
        log.info("ADMIN_ASSIGN_RESTAURANT_OWNER actor={} restaurantId={} ownerUserId={}",
                actor(admin), restaurantId, request.userId());
        return AdminRestaurantResponse.fromDomain(restaurant, LocalDateTime.now(clock));
    }

    private static String actor(Jwt admin) {
        return admin == null ? "unknown" : admin.getSubject();
    }
}
