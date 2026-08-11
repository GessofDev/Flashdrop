package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.catalog.application.usecase.GetProductsByIdsUseCase;
import com.flashdrop.catalog.application.usecase.GetRestaurantByIdUseCase;
import com.flashdrop.catalog.application.usecase.GetRestaurantByUserIdUseCase;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.InternalProductResponse;
import com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto.InternalRestaurantResponse;

@RestController
@RequestMapping("/api/internal")
public class InternalCatalogController {

    private final GetProductsByIdsUseCase getProductsByIdsUseCase;
    private final GetRestaurantByIdUseCase getRestaurantByIdUseCase;
    private final GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase;

    public InternalCatalogController(
            GetProductsByIdsUseCase getProductsByIdsUseCase,
            GetRestaurantByIdUseCase getRestaurantByIdUseCase,
            GetRestaurantByUserIdUseCase getRestaurantByUserIdUseCase
    ) {
        this.getProductsByIdsUseCase = getProductsByIdsUseCase;
        this.getRestaurantByIdUseCase = getRestaurantByIdUseCase;
        this.getRestaurantByUserIdUseCase = getRestaurantByUserIdUseCase;
    }

    @GetMapping("/products")
    public List<InternalProductResponse> getProductsByIds(@RequestParam String ids) {
        List<Long> productIds = parseIds(ids);

        if (productIds.isEmpty()) {
            return List.of();
        }

        return getProductsByIdsUseCase.execute(productIds)
                .stream()
                .map(InternalProductResponse::fromDomain)
                .toList();
    }

    @GetMapping("/restaurants/{restaurantId}")
    public InternalRestaurantResponse getRestaurantById(@PathVariable Long restaurantId) {
        return InternalRestaurantResponse.fromDomain(getRestaurantByIdUseCase.execute(restaurantId));
    }

    @GetMapping(value = "/restaurants", params = "userId")
    public InternalRestaurantResponse getRestaurantByUserId(@RequestParam Long userId) {
        return InternalRestaurantResponse.fromDomain(getRestaurantByUserIdUseCase.execute(userId));
    }

    private List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }

        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .map(Long::parseLong)
                .distinct()
                .toList();
    }
}
