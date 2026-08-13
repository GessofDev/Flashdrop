package com.flashdrop.catalog.domain.model;

import com.flashdrop.catalog.domain.valueobjects.Money;

public class Product {

    // Este es el modelo de negocio: representa un producto dentro del catalogo.
    // No sabe nada de HTTP, Supabase ni SQL; solo guarda datos y reglas propias.
    private Long id;
    private Long categoryId;
    private Long restaurantId;
    private String name;
    private String description;
    private Money price;
    private String image;
    private boolean available;

    public Product(
            Long id,
            Long categoryId,
            Long restaurantId,
            String name,
            String description,
            Money price,
            String image,
            boolean available
    ) {
        // Regla de negocio: no dejamos crear productos sin nombre.
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }

        // El precio viene como Money para concentrar la validacion de monto ahi.
        if (price == null) {
            throw new IllegalArgumentException("El precio del producto no puede ser negativo");
        }

        this.id = id;
        this.categoryId = categoryId;
        this.restaurantId = restaurantId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.image = image;
        this.available = available;

    }

    public Long getId() {
        return id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }

    public String getImage() {
        return image;
    }

    public boolean isAvailable() {
        return available;
    }

}
