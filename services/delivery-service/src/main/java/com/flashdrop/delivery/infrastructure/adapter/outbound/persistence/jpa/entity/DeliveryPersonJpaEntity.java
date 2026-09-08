package com.flashdrop.delivery.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "delivery_persons", schema = "internal")
public class DeliveryPersonJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at")
    private Instant createdAt;

    public DeliveryPersonJpaEntity() {
    }

    public DeliveryPersonJpaEntity(Long id, String userId, Boolean active, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.active = active;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
