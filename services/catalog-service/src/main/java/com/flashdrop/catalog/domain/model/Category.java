package com.flashdrop.catalog.domain.model;

public class Category {

    private Long id;
    private String name;
    private String description;
    private String image;

    public Category(Long id, String name, String description, String image) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoria es obligatorio");
        }

        this.id = id;
        this.name = name;
        this.description = description;
        this.image = image;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImage() {
        return image;
    }
}
