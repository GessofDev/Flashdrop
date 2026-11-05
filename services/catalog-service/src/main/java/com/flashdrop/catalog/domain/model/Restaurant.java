package com.flashdrop.catalog.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public class Restaurant {

    private Long id;
    private Long userId;
    private String name;
    private String address;
    private LocalDateTime createdAt;
    private boolean acceptingOrders;
    private List<OpeningHours> hours;

    public Restaurant(Long id, Long userId, String name, String address, LocalDateTime createdAt) {
        this(id, userId, name, address, createdAt, true, List.of());
    }

    public Restaurant(Long id, Long userId, String name, String address, LocalDateTime createdAt,
                      boolean acceptingOrders, List<OpeningHours> hours) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del restaurante es obligatorio");
        }

        this.id = id;
        this.userId = userId;
        this.name = name;
        this.address = address;
        this.createdAt = createdAt;
        this.acceptingOrders = acceptingOrders;
        this.hours = List.copyOf(hours);
    }

    /**
     * El interruptor manda sobre el horario. Sin horario definido basta el
     * interruptor; con horario, el local atiende solo dentro de sus tramos.
     */
    public boolean isOpenAt(LocalDateTime moment) {
        if (!acceptingOrders) {
            return false;
        }
        return hours.isEmpty() || hours.stream().anyMatch(tramo -> tramo.covers(moment));
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

    public boolean isAcceptingOrders() {
        return acceptingOrders;
    }

    public List<OpeningHours> getHours() {
        return hours;
    }
}
