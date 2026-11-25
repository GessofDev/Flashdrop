package com.flashdrop.catalog.application.usecase;

import org.springframework.stereotype.Service;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;

/**
 * Account deletion (FR-15): removes the user's memberships in merchant_members while preserving
 * the merchant, its stores, and its catalog even if the merchant now has zero members. Idempotent.
 */
@Service
public class EraseRestaurantOwnerUseCase {

    private final MerchantMemberRepositoryPort merchantMembers;

    public EraseRestaurantOwnerUseCase(MerchantMemberRepositoryPort merchantMembers) {
        this.merchantMembers = merchantMembers;
    }

    /** @return true if the user had at least one merchant membership that was deleted; false otherwise. */
    public boolean execute(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId debe ser positivo");
        }
        if (merchantMembers.findByUserId(userId).isEmpty()) {
            return false;
        }
        merchantMembers.deleteByUserId(userId);
        return true;
    }
}
