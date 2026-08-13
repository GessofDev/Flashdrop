package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity;

import java.time.LocalDateTime;

import com.flashdrop.catalog.domain.model.Restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "restaurant")
public class RestaurantEntity {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected RestaurantEntity() {
    }

    public Restaurant toDomain() {
        return new Restaurant(id, userId, name, address, createdAt);
    }
}
