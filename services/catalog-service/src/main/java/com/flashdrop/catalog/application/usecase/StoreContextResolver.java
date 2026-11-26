package com.flashdrop.catalog.application.usecase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.LocalContextRequiredException;
import com.flashdrop.catalog.domain.exception.OwnershipAccessDeniedException;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.Restaurant;

/** Resolves the active store context for an authenticated operator from X-My-Store-Restaurant. */
@Service
public class StoreContextResolver {

    public static final String STORE_HEADER = "X-My-Store-Restaurant";

    private final MerchantMemberRepositoryPort merchantMembers;
    private final RestaurantRepositoryPort restaurants;

    public StoreContextResolver(
            MerchantMemberRepositoryPort merchantMembers,
            RestaurantRepositoryPort restaurants
    ) {
        this.merchantMembers = merchantMembers;
        this.restaurants = restaurants;
    }

    public List<Restaurant> resolveOperableStores(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Map<Long, Restaurant> byId = new LinkedHashMap<>();

        List<Long> merchantIds = merchantMembers.findByUserId(userId).stream()
                .map(MerchantMember::getMerchantId)
                .distinct()
                .toList();

        if (!merchantIds.isEmpty()) {
            for (Restaurant store : restaurants.findByMerchantIds(merchantIds)) {
                byId.putIfAbsent(store.getId(), store);
            }
        }

        return List.copyOf(byId.values());
    }

    public Restaurant resolveActiveStore(Long userId, String rawHeader) {
        List<Restaurant> operable = resolveOperableStores(userId);

        if (rawHeader != null && !rawHeader.isBlank()) {
            long restaurantId;
            try {
                restaurantId = Long.parseLong(rawHeader.trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "El header " + STORE_HEADER + " debe ser un identificador numerico valido");
            }

            if (!restaurants.existsById(restaurantId)) {
                throw new ResourceNotFoundException("Restaurante no encontrado");
            }

            return operable.stream()
                    .filter(store -> store.getId().equals(restaurantId))
                    .findFirst()
                    .orElseThrow(() -> new OwnershipAccessDeniedException(
                            "No tenes permiso para operar este local"));
        }

        if (operable.isEmpty()) {
            throw new ResourceNotFoundException("El usuario no tiene un restaurante asignado");
        }
        if (operable.size() == 1) {
            return operable.get(0);
        }

        throw new LocalContextRequiredException(operable);
    }
}
