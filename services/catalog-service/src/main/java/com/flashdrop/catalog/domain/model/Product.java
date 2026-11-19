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
    /** Dimension del producto, escala 1 a 10 (la usa Orders para el envio). {@code null} si no esta clasificado. */
    private Integer dimension;

    public static final int MIN_DIMENSION = 1;
    public static final int MAX_DIMENSION = 10;

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
        this(id, categoryId, restaurantId, name, description, price, image, available, null);
    }

    public Product(
            Long id,
            Long categoryId,
            Long restaurantId,
            String name,
            String description,
            Money price,
            String image,
            boolean available,
            Integer dimension
    ) {
        // Regla de negocio: no dejamos crear productos sin nombre.
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }

        // La dimension es opcional, pero si viene debe estar en la escala 1 a 10.
        if (dimension != null && (dimension < MIN_DIMENSION || dimension > MAX_DIMENSION)) {
            throw new IllegalArgumentException("La dimension del producto debe estar entre "
                    + MIN_DIMENSION + " y " + MAX_DIMENSION);
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
        this.dimension = dimension;

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

    public Integer getDimension() {
        return dimension;
    }

    public boolean isAvailable() {
        return available;
    }

}
