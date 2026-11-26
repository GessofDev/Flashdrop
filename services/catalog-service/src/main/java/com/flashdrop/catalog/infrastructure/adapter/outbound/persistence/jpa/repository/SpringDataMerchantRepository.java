package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity.MerchantEntity;

public interface SpringDataMerchantRepository extends JpaRepository<MerchantEntity, Long> {
}
