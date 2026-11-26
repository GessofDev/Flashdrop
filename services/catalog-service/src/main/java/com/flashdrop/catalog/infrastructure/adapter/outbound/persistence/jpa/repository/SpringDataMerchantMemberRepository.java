package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity.MerchantMemberEntity;

public interface SpringDataMerchantMemberRepository extends JpaRepository<MerchantMemberEntity, Long> {

    List<MerchantMemberEntity> findByUserId(Long userId);

    Optional<MerchantMemberEntity> findByMerchantIdAndUserId(Long merchantId, Long userId);

    void deleteByUserId(Long userId);
}
