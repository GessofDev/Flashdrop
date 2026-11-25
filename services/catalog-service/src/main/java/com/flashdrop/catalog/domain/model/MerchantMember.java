package com.flashdrop.catalog.domain.model;

import java.time.LocalDateTime;

/** Membership linking an operator user to a merchant with a role. */
public class MerchantMember {

    private final Long id;
    private final Long merchantId;
    private final Long userId;
    private final MerchantRole role;
    private final LocalDateTime createdAt;

    public MerchantMember(Long id, Long merchantId, Long userId, MerchantRole role, LocalDateTime createdAt) {
        if (merchantId == null || userId == null || role == null) {
            throw new IllegalArgumentException("merchantId, userId, and role are required");
        }
        this.id = id;
        this.merchantId = merchantId;
        this.userId = userId;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public Long getUserId() {
        return userId;
    }

    public MerchantRole getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
