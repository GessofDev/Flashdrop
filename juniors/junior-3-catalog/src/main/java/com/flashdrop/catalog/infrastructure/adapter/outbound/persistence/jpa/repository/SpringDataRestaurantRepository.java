package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity.RestaurantEntity;

public interface SpringDataRestaurantRepository extends JpaRepository<RestaurantEntity, Long> {

    List<RestaurantEntity> findAllByOrderByNameAsc();
}
