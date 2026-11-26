package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity;

import java.time.LocalDateTime;

import com.flashdrop.catalog.domain.model.Merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "merchants")
public class MerchantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "tax_id")
    private String taxId;

    private String phone;

    private String email;

    @Column(nullable = false)
    private String status = "ACTIVO";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected MerchantEntity() {
    }

    public static MerchantEntity create(String name) {
        MerchantEntity entity = new MerchantEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.name = name;
        entity.status = "ACTIVO";
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public Merchant toDomain() {
        return new Merchant(id, name, legalName, taxId, phone, email, status, createdAt, updatedAt);
    }
}
