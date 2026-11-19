package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "delivery_routes", schema = "internal")
public class DeliveryRouteJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "delivery_person_id")
    private Long deliveryPersonId;

    @Column(name = "pickup_address")
    private String pickupAddress;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "commune", length = 120)
    private String commune;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(nullable = false)
    private String status;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "reception_mode", nullable = false)
    private String receptionMode = "NORMAL";

    @Column(name = "pickup_latitude", precision = 9, scale = 6)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 9, scale = 6)
    private BigDecimal pickupLongitude;

    @Column(name = "client_user_id", length = 64)
    private String clientUserId;

    public DeliveryRouteJpaEntity() {
    }

    public DeliveryRouteJpaEntity(Long id, Long orderId, String pickupAddress, String deliveryAddress,
                                   BigDecimal distanceKm, Integer estimatedMinutes, String status, Instant createdAt) {
        this(id, orderId, null, pickupAddress, deliveryAddress, distanceKm, estimatedMinutes, status, createdAt);
    }

    public DeliveryRouteJpaEntity(Long id, Long orderId, Long deliveryPersonId, String pickupAddress,
                                   String deliveryAddress, BigDecimal distanceKm, Integer estimatedMinutes,
                                   String status, Instant createdAt) {
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

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getReceptionMode() {
        return receptionMode;
    }

    public void setReceptionMode(String receptionMode) {
        this.receptionMode = receptionMode;
    }

    public String getCommune() {
        return commune;
    }

    public void setCommune(String commune) {
        this.commune = commune;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getPickupLatitude() {
        return pickupLatitude;
    }

    public void setPickupLatitude(BigDecimal pickupLatitude) {
        this.pickupLatitude = pickupLatitude;
    }

    public BigDecimal getPickupLongitude() {
        return pickupLongitude;
    }

    public void setPickupLongitude(BigDecimal pickupLongitude) {
        this.pickupLongitude = pickupLongitude;
    }

    public String getClientUserId() {
        return clientUserId;
    }

    public void setClientUserId(String clientUserId) {
        this.clientUserId = clientUserId;
    }
}
