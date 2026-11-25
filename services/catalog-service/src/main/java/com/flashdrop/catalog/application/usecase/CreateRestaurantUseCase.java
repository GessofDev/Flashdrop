package com.flashdrop.catalog.application.usecase;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.Restaurant;
import com.flashdrop.catalog.domain.valueobjects.Coordinates;

/**
 * Alta administrativa de un restaurante (F6). Nace cerrado: el dueño lo abre cuando configura su
 * horario. El dueño es opcional; si viene, se vincula a su marca (creándola si no existe, D11).
 */
@Service
public class CreateRestaurantUseCase {

    private final RestaurantRepositoryPort restaurants;
    private final MerchantRepositoryPort merchants;
    private final MerchantMemberRepositoryPort merchantMembers;

    public CreateRestaurantUseCase(
            RestaurantRepositoryPort restaurants,
            MerchantRepositoryPort merchants,
            MerchantMemberRepositoryPort merchantMembers
    ) {
        this.restaurants = restaurants;
        this.merchants = merchants;
        this.merchantMembers = merchantMembers;
    }

    public Restaurant execute(String name, String address, Long ownerUserId) {
        return execute(name, address, ownerUserId, null, null);
    }

    public Restaurant execute(String name, String address, Long ownerUserId,
                              BigDecimal latitude, BigDecimal longitude) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del restaurante es obligatorio");
        }
        Coordinates.ofNullable(latitude, longitude);
        if (ownerUserId != null && ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId debe ser positivo");
        }
        String trimmedName = name.trim();
        String normalizedAddress = address == null || address.isBlank() ? null : address.trim();
        if (ownerUserId == null) {
            return restaurants.create(trimmedName, normalizedAddress, null, false, latitude, longitude);
        }
        Long merchantId = MerchantOwnershipSupport.resolveOrCreateMerchantId(
                restaurants, merchants, merchantMembers, ownerUserId, trimmedName);
        return restaurants.createInMerchant(
                trimmedName, normalizedAddress, ownerUserId, merchantId, false, latitude, longitude, null);
    }
}
