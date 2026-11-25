package com.flashdrop.catalog.application.usecase;

import java.util.List;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.RestaurantRepositoryPort;
import com.flashdrop.catalog.domain.model.Merchant;
import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;
import com.flashdrop.catalog.domain.model.Restaurant;

/**
 * Shared D11 helper: resolves an owner's existing merchant or auto-creates one with a DUEÑO
 * membership, linking any legacy store that lacks a merchant.
 */
final class MerchantOwnershipSupport {

    private MerchantOwnershipSupport() {
    }

    static Long resolveOrCreateMerchantId(
            RestaurantRepositoryPort restaurants,
            MerchantRepositoryPort merchants,
            MerchantMemberRepositoryPort merchantMembers,
            Long ownerUserId,
            String fallbackMerchantName
    ) {
        List<MerchantMember> memberships = merchantMembers.findByUserId(ownerUserId);
        if (!memberships.isEmpty()) {
            return memberships.stream()
                    .filter(member -> member.getRole() == MerchantRole.DUEÑO)
                    .findFirst()
                    .orElse(memberships.get(0))
                    .getMerchantId();
        }

        Restaurant legacyStore = restaurants.findByUserId(ownerUserId).orElse(null);
        if (legacyStore != null && legacyStore.getMerchantId() != null) {
            merchantMembers.addMember(legacyStore.getMerchantId(), ownerUserId, MerchantRole.DUEÑO);
            return legacyStore.getMerchantId();
        }

        String merchantName = legacyStore != null ? legacyStore.getName() : fallbackMerchantName;
        Merchant createdMerchant = merchants.create(merchantName);
        merchantMembers.addMember(createdMerchant.getId(), ownerUserId, MerchantRole.DUEÑO);
        if (legacyStore != null) {
            restaurants.assignMerchant(legacyStore.getId(), createdMerchant.getId());
        }
        return createdMerchant.getId();
    }
}
