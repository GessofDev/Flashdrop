package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity.ProductEntity;

public interface SpringDataProductRepository extends JpaRepository<ProductEntity, Long> {

    List<ProductEntity> findByIdIn(Collection<Long> ids);
}
