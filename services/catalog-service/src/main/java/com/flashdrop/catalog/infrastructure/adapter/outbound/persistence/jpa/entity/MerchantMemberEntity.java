package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity;

import java.time.LocalDateTime;

import com.flashdrop.catalog.domain.model.MerchantMember;
import com.flashdrop.catalog.domain.model.MerchantRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "merchant_members")
public class MerchantMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantRole role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected MerchantMemberEntity() {
    }

    public static MerchantMemberEntity create(Long merchantId, Long userId, MerchantRole role) {
        MerchantMemberEntity entity = new MerchantMemberEntity();
        entity.merchantId = merchantId;
        entity.userId = userId;
        entity.role = role;
        entity.createdAt = LocalDateTime.now();
        return entity;
    }

    public MerchantMember toDomain() {
        return new MerchantMember(id, merchantId, userId, role, createdAt);
    }
}
