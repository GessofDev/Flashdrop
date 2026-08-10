package com.flashdrop.catalog.domain.model;

import java.time.LocalDateTime;

public class Restaurant {

    private Long id;
    private Long userId;
    private String name;
    private String address;
    private LocalDateTime createdAt;

    public Restaurant(Long id, Long userId, String name, String address, LocalDateTime createdAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del restaurante es obligatorio");
        }

        this.id = id;
        this.userId = userId;
        this.name = name;
        this.address = address;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
