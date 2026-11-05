package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.catalog.application.usecase.GetRestaurantByUserIdUseCase;
import com.flashdrop.catalog.application.usecase.SetStoreHoursUseCase;
import com.flashdrop.catalog.application.usecase.SetStoreOpenStatusUseCase;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.OpeningHoursDto;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.RestaurantResponse;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.UpdateStoreHoursRequest;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.UpdateStoreOpenStatusRequest;

import jakarta.validation.Valid;

/** Apertura del local del usuario autenticado. SecurityConfig exige el rol Restaurante. */
@RestController
@RequestMapping("/api/catalog/my/restaurant")
public class MyStoreRestaurantController {

    private final GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase;
    private final SetStoreOpenStatusUseCase setStoreOpenStatusUseCase;
    private final SetStoreHoursUseCase setStoreHoursUseCase;
    private final Clock clock;

    public MyStoreRestaurantController(
            GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase,
            SetStoreOpenStatusUseCase setStoreOpenStatusUseCase,
            SetStoreHoursUseCase setStoreHoursUseCase,
            Clock clock
    ) {
        this.getRestaurantByUserIdUseCase = getRestaurantByUserIdUseCase;
        this.setStoreOpenStatusUseCase = setStoreOpenStatusUseCase;
        this.setStoreHoursUseCase = setStoreHoursUseCase;
        this.clock = clock;
    }

    @GetMapping
    public RestaurantResponse get(@AuthenticationPrincipal Jwt jwt) {
        return toResponse(getRestaurantByUserIdUseCase.execute(userId(jwt)));
    }

    @PutMapping("/open-status")
    public RestaurantResponse updateOpenStatus(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateStoreOpenStatusRequest request
    ) {
        return toResponse(setStoreOpenStatusUseCase.execute(userId(jwt), request.acceptingOrders()));
    }

    @PutMapping("/hours")
    public RestaurantResponse updateHours(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateStoreHoursRequest request
    ) {
        return toResponse(setStoreHoursUseCase.execute(
                userId(jwt),
                request.hours().stream().map(OpeningHoursDto::toDomain).toList()
        ));
    }

    private RestaurantResponse toResponse(Restaurant restaurant) {
        return RestaurantResponse.fromDomain(restaurant, LocalDateTime.now(clock));
    }

    private Long userId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new OwnershipAccessDeniedException("El identificador del usuario autenticado no es valido");
        }
    }
}
