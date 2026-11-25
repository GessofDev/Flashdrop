package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class GetRestaurantByUserIdUseCase {

    private final StoreContextResolver storeContextResolver;

    public GetRestaurantByUserIdUseCase(StoreContextResolver storeContextResolver) {
        this.storeContextResolver = storeContextResolver;
    }

    public Restaurant execute(Long userId) {
        return storeContextResolver.resolveOperableStores(userId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found for userId: " + userId
                ));
    }
}
