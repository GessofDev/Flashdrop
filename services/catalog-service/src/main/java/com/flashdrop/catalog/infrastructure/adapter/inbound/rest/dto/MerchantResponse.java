package com.flashdrop.catalog.infrastructure.adapter.inbound.rest.dto;

import com.flashdrop.catalog.domain.model.Merchant;
import com.flashdrop.catalog.domain.model.MerchantRole;

/** Brand profile returned for the authenticated store operator. */
public record MerchantResponse(
        Long id,
        String name,
        String legalName,
        String taxId,
        String phone,
        String email,
        String status,
        String role
) {
    public static MerchantResponse fromDomain(Merchant merchant, MerchantRole role) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getName(),
                merchant.getLegalName(),
                merchant.getTaxId(),
                merchant.getPhone(),
                merchant.getEmail(),
                merchant.getStatus(),
                role == null ? null : role.name()
        );
    }
}
