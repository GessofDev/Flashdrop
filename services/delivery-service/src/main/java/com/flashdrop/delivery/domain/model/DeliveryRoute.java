package com.flashdrop.delivery.domain.model;

import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;

import java.time.Instant;

public class DeliveryRoute {

    private Long id;
    private Long orderId;
    private String pickupAddress;
    private String deliveryAddress;
    private Distance distanceKm;
    private EstimatedTime estimatedMinutes;
    private RouteStatus status;
    private Instant createdAt;

    public DeliveryRoute() {
    }

    public DeliveryRoute(Long id, Long orderId, String pickupAddress, String deliveryAddress,
                         Distance distanceKm, EstimatedTime estimatedMinutes, RouteStatus status,
                         Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.distanceKm = distanceKm;
        this.estimatedMinutes = estimatedMinutes;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(String pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public Distance getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Distance distanceKm) {
        this.distanceKm = distanceKm;
    }

    public EstimatedTime getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(EstimatedTime estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public RouteStatus getStatus() {
        return status;
    }

    public void setStatus(RouteStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}