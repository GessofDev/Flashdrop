package com.flashdrop.delivery.domain.model;

import com.flashdrop.delivery.domain.valueobjects.Distance;
import com.flashdrop.delivery.domain.valueobjects.EstimatedTime;
import com.flashdrop.delivery.domain.valueobjects.RouteStatus;

import java.time.Instant;

public class DeliveryRoute {

    private Long id;
    private Long orderId;
    private Long deliveryPersonId;
    private String pickupAddress;
    private String deliveryAddress;
    private Distance distanceKm;
    private EstimatedTime estimatedMinutes;
    private RouteStatus status;
    private Instant createdAt;
    /** NORMAL (codigo fijo 000000) o SEGURO (codigo unico del cliente). */
    private String receptionMode = RECEPTION_MODE_NORMAL;

    public static final String RECEPTION_MODE_NORMAL = "NORMAL";
    public static final String RECEPTION_MODE_SEGURO = "SEGURO";

    public DeliveryRoute() {
    }

    public DeliveryRoute(Long id, Long orderId, Long deliveryPersonId, String pickupAddress,
                         String deliveryAddress, Distance distanceKm, EstimatedTime estimatedMinutes,
                         RouteStatus status, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.deliveryPersonId = deliveryPersonId;
        this.pickupAddress = pickupAddress;
        this.deliveryAddress = deliveryAddress;
        this.distanceKm = distanceKm;
        this.estimatedMinutes = estimatedMinutes;
        this.status = status;
        this.createdAt = createdAt;
    }

    public DeliveryRoute(Long id, Long orderId, String pickupAddress, String deliveryAddress,
                         Distance distanceKm, EstimatedTime estimatedMinutes, RouteStatus status,
                         Instant createdAt) {
        this(id, orderId, null, pickupAddress, deliveryAddress, distanceKm, estimatedMinutes, status, createdAt);
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

    public Long getDeliveryPersonId() {
        return deliveryPersonId;
    }

    public void setDeliveryPersonId(Long deliveryPersonId) {
        this.deliveryPersonId = deliveryPersonId;
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

    public String getReceptionMode() {
        return receptionMode;
    }

    public void setReceptionMode(String receptionMode) {
        this.receptionMode = receptionMode == null || receptionMode.isBlank()
                ? RECEPTION_MODE_NORMAL
                : receptionMode.trim().toUpperCase();
    }

    /** La app del repartidor debe pedir el codigo solo en pedidos SEGURO. */
    public boolean isDeliveryPinRequired() {
        return RECEPTION_MODE_SEGURO.equals(receptionMode);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}