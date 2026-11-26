package com.flashdrop.catalog.application.usecase;

import java.util.List;

import com.flashdrop.catalog.application.port.outbound.MerchantMemberRepositoryPort;
import com.flashdrop.catalog.application.port.outbound.MerchantRepositoryPort;
import com.flashdrop.catalog.domain.model.Merchant;
import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;

/**
 * Shared D11 helper: resolves an owner's existing merchant or auto-creates one with a DUEÑO
 * membership.
 */
final class MerchantOwnershipSupport {

    private MerchantOwnershipSupport() {
    }

    static Long resolveOrCreateMerchantId(
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

        Merchant createdMerchant = merchants.create(fallbackMerchantName);
        merchantMembers.addMember(createdMerchant.getId(), ownerUserId, MerchantRole.DUEÑO);
        return createdMerchant.getId();
    }
}
