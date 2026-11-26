package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.application.usecase.GetRestaurantRatingStatsUseCase;
import com.flashdrop.catalog.application.usecase.StoreContextResolver;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Merchant;
import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;
import com.flashdrop.catalog.domain.model.RatingSummary;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.CreateMyStoreRequest;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.MerchantResponse;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.RestaurantResponse;

import jakarta.validation.Valid;

/** Merchant profile and multi-store management for authenticated store operators. */
@RestController
@RequestMapping("/api/catalog/my")
public class MyStoreMerchantController {

    private final StoreContextResolver storeContextResolver;
    private final MerchantRepositoryPort merchantRepository;
    private final MerchantMemberRepositoryPort merchantMemberRepository;
    private final RestaurantRepositoryPort restaurantRepository;
    private final GetRestaurantRatingStatsUseCase getRestaurantRatingStatsUseCase;
    private final Clock clock;

    public MyStoreMerchantController(
            StoreContextResolver storeContextResolver,
            MerchantRepositoryPort merchantRepository,
            MerchantMemberRepositoryPort merchantMemberRepository,
            RestaurantRepositoryPort restaurantRepository,
            GetRestaurantRatingStatsUseCase getRestaurantRatingStatsUseCase,
            Clock clock
    ) {
        this.storeContextResolver = storeContextResolver;
        this.merchantRepository = merchantRepository;
        this.merchantMemberRepository = merchantMemberRepository;
        this.restaurantRepository = restaurantRepository;
        this.getRestaurantRatingStatsUseCase = getRestaurantRatingStatsUseCase;
        this.clock = clock;
    }

    @GetMapping("/merchant")
    public MerchantResponse getMerchant(@AuthenticationPrincipal Jwt jwt) {
        Long userId = userId(jwt);
        List<MerchantMember> memberships = merchantMemberRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            throw new ResourceNotFoundException("El usuario no tiene una marca asignada");
        }
        MerchantMember member = memberships.get(0);
        Merchant merchant = merchantRepository.findById(member.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada"));
        return MerchantResponse.fromDomain(merchant, member.getRole());
    }

    @GetMapping("/restaurants")
    public List<RestaurantResponse> listRestaurants(@AuthenticationPrincipal Jwt jwt) {
        Long userId = userId(jwt);
        List<Restaurant> stores = storeContextResolver.resolveOperableStores(userId);
        if (stores.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, RatingSummary> ratings = getRestaurantRatingStatsUseCase.execute(
                stores.stream().map(Restaurant::getId).toList());
        List<Long> merchantIds = stores.stream()
                .map(Restaurant::getMerchantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Merchant> merchants = merchantRepository.findByIds(merchantIds);

        return stores.stream()
                .map(store -> {
                    Merchant merchant = store.getMerchantId() == null ? null : merchants.get(store.getMerchantId());
                    return RestaurantResponse.fromDomain(
                            store,
                            now,
                            ratings.getOrDefault(store.getId(), RatingSummary.none()),
                            merchant == null ? null : merchant.getName());
                })
                .toList();
    }

    @PostMapping("/restaurants")
    public ResponseEntity<RestaurantResponse> createRestaurant(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMyStoreRequest request
    ) {
        Long userId = userId(jwt);
        Merchant merchant = resolveOrAutoCreateMerchantForCreation(userId, request.name());

        Restaurant created = restaurantRepository.createInMerchant(
                request.name().trim(),
                request.address(),
                merchant.getId(),
                false,
                request.latitude(),
                request.longitude(),
                request.deliveryRadiusKm()
        );

        RestaurantResponse response = RestaurantResponse.fromDomain(
                created,
                LocalDateTime.now(clock),
                RatingSummary.none(),
                merchant.getName()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private Merchant resolveOrAutoCreateMerchantForCreation(Long userId, String fallbackMerchantName) {
        List<MerchantMember> memberships = merchantMemberRepository.findByUserId(userId);
        if (!memberships.isEmpty()) {
            MerchantMember ownerMembership = memberships.stream()
                    .filter(member -> member.getRole() == MerchantRole.DUEÑO)
                    .findFirst()
                    .orElseThrow(() -> new OwnershipAccessDeniedException(
                            "Solo el dueño de la marca puede crear nuevos locales"));
            return merchantRepository.findById(ownerMembership.getMerchantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Marca no encontrada"));
        }

        // Auto-create merchant if user has none (D11).
        Merchant createdMerchant = merchantRepository.create(fallbackMerchantName.trim());
        merchantMemberRepository.addMember(createdMerchant.getId(), userId, MerchantRole.DUEÑO);
        return createdMerchant;
    }

    private Long userId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new OwnershipAccessDeniedException("El identificador del usuario autenticado no es valido");
        }
    }
}
