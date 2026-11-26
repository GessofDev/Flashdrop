package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.application.usecase.GetRestaurantRatingStatsUseCase;
import com.flashdrop.catalog.application.usecase.SetStoreHoursUseCase;
import com.flashdrop.catalog.application.usecase.SetStoreLocationUseCase;
import com.flashdrop.catalog.application.usecase.SetStoreDeliveryRadiusUseCase;
import com.flashdrop.catalog.application.usecase.SetStoreOpenStatusUseCase;
import com.flashdrop.catalog.application.usecase.StoreContextResolver;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.model.Merchant;
import com.flashdrop.catalog.domain.model.RatingSummary;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.OpeningHoursDto;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.RestaurantResponse;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.UpdateStoreHoursRequest;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.UpdateStoreLocationRequest;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.UpdateStoreDeliveryRadiusRequest;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.UpdateStoreOpenStatusRequest;

import jakarta.validation.Valid;

/** Apertura del local del usuario autenticado. SecurityConfig exige el rol Restaurante. */
@RestController
@RequestMapping("/api/catalog/my/restaurant")
public class MyStoreRestaurantController {

    private final StoreContextResolver storeContextResolver;
    private final SetStoreOpenStatusUseCase setStoreOpenStatusUseCase;
    private final SetStoreHoursUseCase setStoreHoursUseCase;
    private final SetStoreLocationUseCase setStoreLocationUseCase;
    private final SetStoreDeliveryRadiusUseCase setStoreDeliveryRadiusUseCase;
    private final GetRestaurantRatingStatsUseCase getRestaurantRatingStatsUseCase;
    private final MerchantRepositoryPort merchantRepositoryPort;
    private final Clock clock;

    public MyStoreRestaurantController(
            StoreContextResolver storeContextResolver,
            SetStoreOpenStatusUseCase setStoreOpenStatusUseCase,
            SetStoreHoursUseCase setStoreHoursUseCase,
            SetStoreLocationUseCase setStoreLocationUseCase,
            SetStoreDeliveryRadiusUseCase setStoreDeliveryRadiusUseCase,
            GetRestaurantRatingStatsUseCase getRestaurantRatingStatsUseCase,
            MerchantRepositoryPort merchantRepositoryPort,
            Clock clock
    ) {
        this.storeContextResolver = storeContextResolver;
        this.setStoreOpenStatusUseCase = setStoreOpenStatusUseCase;
        this.setStoreHoursUseCase = setStoreHoursUseCase;
        this.setStoreLocationUseCase = setStoreLocationUseCase;
        this.setStoreDeliveryRadiusUseCase = setStoreDeliveryRadiusUseCase;
        this.getRestaurantRatingStatsUseCase = getRestaurantRatingStatsUseCase;
        this.merchantRepositoryPort = merchantRepositoryPort;
        this.clock = clock;
    }

    @GetMapping
    public RestaurantResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = StoreContextResolver.STORE_HEADER, required = false) String restaurantHeader
    ) {
        return toResponse(storeContextResolver.resolveActiveStore(userId(jwt), restaurantHeader));
    }

    @PutMapping("/open-status")
    public RestaurantResponse updateOpenStatus(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = StoreContextResolver.STORE_HEADER, required = false) String storeHeader,
            @Valid @RequestBody UpdateStoreOpenStatusRequest request
    ) {
        Restaurant store = storeContextResolver.resolveActiveStore(userId(jwt), storeHeader);
        return toResponse(setStoreOpenStatusUseCase.execute(store.getId(), request.acceptingOrders()));
    }

    @PutMapping("/hours")
    public RestaurantResponse updateHours(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = StoreContextResolver.STORE_HEADER, required = false) String storeHeader,
            @Valid @RequestBody UpdateStoreHoursRequest request
    ) {
        Restaurant store = storeContextResolver.resolveActiveStore(userId(jwt), storeHeader);
        return toResponse(setStoreHoursUseCase.execute(
                store.getId(),
                request.hours().stream().map(OpeningHoursDto::toDomain).toList()
        ));
    }

    @PutMapping("/location")
    public RestaurantResponse updateLocation(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = StoreContextResolver.STORE_HEADER, required = false) String storeHeader,
            @Valid @RequestBody UpdateStoreLocationRequest request
    ) {
        Restaurant store = storeContextResolver.resolveActiveStore(userId(jwt), storeHeader);
        return toResponse(setStoreLocationUseCase.execute(
                store.getId(),
                request.latitude(),
                request.longitude()
        ));
    }

    @PutMapping("/delivery-radius")
    public RestaurantResponse updateDeliveryRadius(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = StoreContextResolver.STORE_HEADER, required = false) String storeHeader,
            @Valid @RequestBody UpdateStoreDeliveryRadiusRequest request
    ) {
        Restaurant store = storeContextResolver.resolveActiveStore(userId(jwt), storeHeader);
        return toResponse(setStoreDeliveryRadiusUseCase.execute(store.getId(), request.deliveryRadiusKm()));
    }

    private RestaurantResponse toResponse(Restaurant restaurant) {
        // El promedio sale de la cache local (pull a reviews-service), no de
        // una llamada en linea: el local abre igual si reviews-service caе.
        RatingSummary rating = getRestaurantRatingStatsUseCase
                .execute(List.of(restaurant.getId()))
                .getOrDefault(restaurant.getId(), RatingSummary.none());
        String merchantName = restaurant.getMerchantId() == null
                ? null
                : merchantRepositoryPort.findById(restaurant.getMerchantId())
                        .map(Merchant::getName)
                        .orElse(null);
        return RestaurantResponse.fromDomain(restaurant, LocalDateTime.now(clock), rating, merchantName);
    }

    private Long userId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new OwnershipAccessDeniedException("El identificador del usuario autenticado no es valido");
        }
    }
}
