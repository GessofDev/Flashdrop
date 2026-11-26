package com.flashdrop.catalog.application.port.outbound;

import java.util.List;
import java.util.Optional;

import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;

/** Persistence port for merchant-user memberships. */
public interface MerchantMemberRepositoryPort {

    List<MerchantMember> findByUserId(Long userId);

    Optional<MerchantMember> findByMerchantIdAndUserId(Long merchantId, Long userId);

    MerchantMember addMember(Long merchantId, Long userId, MerchantRole role);

    void deleteByUserId(Long userId);
}
