package com.flashdrop.delivery.domain.model;

import com.flashdrop.delivery.domain.valueobjects.VehicleType;

import java.time.Instant;

public class DeliveryPerson {

    private Long id;
    private String userId;
    private VehicleType vehicle;
    private Instant createdAt;

    public DeliveryPerson() {
    }

    public DeliveryPerson(Long id, String userId, VehicleType vehicle, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.vehicle = vehicle;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public VehicleType getVehicle() {
        return vehicle;
    }

    public void setVehicle(VehicleType vehicle) {
        this.vehicle = vehicle;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}