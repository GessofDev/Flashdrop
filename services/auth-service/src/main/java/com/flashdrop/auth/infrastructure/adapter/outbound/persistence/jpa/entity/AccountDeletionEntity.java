package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_deletions")
public class AccountDeletionEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "orders_done", nullable = false)
    private boolean ordersDone;

    @Column(name = "delivery_done", nullable = false)
    private boolean deliveryDone;

    @Column(name = "catalog_done", nullable = false)
    private boolean catalogDone;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountDeletionEntity() {
    }

    public AccountDeletionEntity(Long userId, Instant now) {
        this.userId = userId;
        this.status = "PENDING";
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getUserId() { return userId; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public boolean isOrdersDone() { return ordersDone; }
    public boolean isDeliveryDone() { return deliveryDone; }
    public boolean isCatalogDone() { return catalogDone; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }

    public void apply(String status, int attempts, boolean ordersDone, boolean deliveryDone, boolean catalogDone,
                      Instant nextAttemptAt, String lastError) {
        this.status = status;
        this.attempts = attempts;
        this.ordersDone = ordersDone;
        this.deliveryDone = deliveryDone;
        this.catalogDone = catalogDone;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError == null || lastError.length() <= 1000 ? lastError : lastError.substring(0, 1000);
        this.updatedAt = Instant.now();
    }
}
