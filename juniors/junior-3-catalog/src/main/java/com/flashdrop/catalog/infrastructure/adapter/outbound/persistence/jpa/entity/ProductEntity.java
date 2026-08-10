package com.flashdrop.catalog.infrastructure.adapter.outbound.persistence.jpa.entity;

import java.math.BigDecimal;

import com.flashdrop.catalog.domain.model.Product;
import com.flashdrop.catalog.domain.valueobjects.Money;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    private String image;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    protected ProductEntity() {
    }

    public ProductEntity(
            Long categoryId,
            Long restaurantId,
            String name,
            String description,
            BigDecimal price,
            String image,
            boolean available
    ) {
        this.categoryId = categoryId;
        this.restaurantId = restaurantId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.image = image;
        this.available = available;
    }

    public Product toDomain() {
        return new Product(
                id,
                categoryId,
                restaurantId,
                name,
                description,
                new Money(price),
                image,
                available
        );
    }
}
