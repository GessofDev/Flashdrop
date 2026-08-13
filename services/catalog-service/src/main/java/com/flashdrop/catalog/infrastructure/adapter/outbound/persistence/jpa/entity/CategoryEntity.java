package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity;

import com.flashdrop.catalog.domain.model.Category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class CategoryEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    private String image;

    protected CategoryEntity() {
    }

    public Category toDomain() {
        return new Category(id, name, description, image);
    }
}
