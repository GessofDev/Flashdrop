package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.application.usecase.GetRestaurantRatingStatsUseCase;
import com.flashdrop.catalog.application.usecase.ListRestaurantsUseCase;
import com.flashdrop.catalog.domain.model.Merchant;
import com.flashdrop.catalog.domain.model.RatingSummary;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.domain.valueobjects.Coordinates;
import com.flashdrop.catalog.domain.valueobjects.DeliveryCoverage;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.RestaurantResponse;

@RestController
@RequestMapping("/catalog/restaurants")
public class RestaurantController {

    // Entrada HTTP para restaurantes. El controller solo orquesta la respuesta.
    private final ListRestaurantsUseCase listRestaurantsUseCase;
    private final GetRestaurantRatingStatsUseCase getRestaurantRatingStatsUseCase;
    private final MerchantRepositoryPort merchantRepositoryPort;
    private final Clock clock;

    public RestaurantController(
            ListRestaurantsUseCase listRestaurantsUseCase,
            GetRestaurantRatingStatsUseCase getRestaurantRatingStatsUseCase,
            MerchantRepositoryPort merchantRepositoryPort,
            Clock clock
    ) {
        this.listRestaurantsUseCase = listRestaurantsUseCase;
        this.getRestaurantRatingStatsUseCase = getRestaurantRatingStatsUseCase;
        this.merchantRepositoryPort = merchantRepositoryPort;
        this.clock = clock;
    }

    @GetMapping
    public List<RestaurantResponse> listRestaurants(
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude
    ) {
        // GET /catalog/restaurants: entrega los restaurantes disponibles.
        LocalDateTime now = LocalDateTime.now(clock);
        Coordinates destination = Coordinates.ofNullable(latitude, longitude);
        List<Restaurant> restaurants = listRestaurantsUseCase.execute().stream()
                .filter(restaurant -> destination == null || DeliveryCoverage.covers(restaurant, destination))
                .toList();

        // El promedio sale de la cache local: el marketplace no depende de que
        // reviews-service este disponible.
        Map<Long, RatingSummary> ratings = getRestaurantRatingStatsUseCase.execute(
                restaurants.stream().map(Restaurant::getId).toList());
        List<Long> merchantIds = restaurants.stream()
                .map(Restaurant::getMerchantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Merchant> merchants = merchantRepositoryPort.findByIds(merchantIds);

        return restaurants.stream()
                .map(restaurant -> {
                    Merchant merchant = restaurant.getMerchantId() == null
                            ? null
                            : merchants.get(restaurant.getMerchantId());
                    return RestaurantResponse.fromDomain(
                            restaurant,
                            now,
                            ratings.getOrDefault(restaurant.getId(), RatingSummary.none()),
                            merchant == null ? null : merchant.getName());
                })
                .toList();
    }
}
