package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.exception.ResourceNotFoundException;
import com.flashdrop.catalog.domain.model.MerchantRole;
import com.flashdrop.catalog.domain.model.Restaurant;

@Service
public class AssignRestaurantOwnerUseCase {

    private final RestaurantRepositoryPort restaurants;
    private final MerchantRepositoryPort merchants;
    private final MerchantMemberRepositoryPort merchantMembers;

    public AssignRestaurantOwnerUseCase(
            RestaurantRepositoryPort restaurants,
            MerchantRepositoryPort merchants,
            MerchantMemberRepositoryPort merchantMembers
    ) {
        this.restaurants = restaurants;
        this.merchants = merchants;
        this.merchantMembers = merchantMembers;
    }

    public Restaurant execute(Long restaurantId, Long userId) {
        if (restaurantId == null || restaurantId <= 0 || userId == null || userId <= 0) {
            throw new IllegalArgumentException("restaurantId y userId deben ser positivos");
        }

        Restaurant restaurant = restaurants.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found with id: " + restaurantId));

        if (restaurant.getMerchantId() != null) {
            merchantMembers.addMember(restaurant.getMerchantId(), userId, MerchantRole.DUEÑO);
        } else {
            Long merchantId = MerchantOwnershipSupport.resolveOrCreateMerchantId(
                    restaurants, merchants, merchantMembers, userId, restaurant.getName());
            restaurants.assignMerchant(restaurant.getId(), merchantId);
        }

        return restaurants.assignOwner(restaurant.getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found with id: " + restaurantId));
    }
}
