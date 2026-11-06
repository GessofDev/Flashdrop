package com.flashdrop.catalog.infrastructure.adapter.inbound.rest;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flashdrop.catalog.application.usecase.EraseRestaurantOwnerUseCase;

/** Eliminacion de cuenta (F5): solo llamadas internas con X-Internal-Api-Key. Idempotente. */
@RestController
@RequestMapping("/api/internal/erasure")
public class InternalErasureController {

    private final EraseRestaurantOwnerUseCase eraseRestaurantOwner;

    public InternalErasureController(EraseRestaurantOwnerUseCase eraseRestaurantOwner) {
        this.eraseRestaurantOwner = eraseRestaurantOwner;
    }

    @PostMapping("/restaurant-owner")
    public Map<String, Boolean> eraseOwner(@RequestParam Long userId) {
        return Map.of("erased", eraseRestaurantOwner.execute(userId));
    }
}
