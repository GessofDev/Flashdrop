package com.flashdrop.catalog.domain.model;

import java.time.LocalDateTime;

/** Brand / merchant group that owns one or more restaurant stores. */
public class Merchant {

    private final Long id;
    private final String name;
    private final String legalName;
    private final String taxId;
    private final String phone;
    private final String email;
    private final String status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Merchant(Long id, String name, String legalName, String taxId,
                    String phone, String email, String status,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Merchant name is required");
        }
        this.id = id;
        this.name = name;
        this.legalName = legalName;
        this.taxId = taxId;
        this.phone = phone;
        this.email = email;
        this.status = status == null || status.isBlank() ? "ACTIVO" : status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTaxId() {
        return taxId;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
