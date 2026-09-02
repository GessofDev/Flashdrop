package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.*;

/** Tabla `roles`. `route` es la pantalla inicial del rol en la app. */
@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String image;

    @Column(nullable = false)
    private String route;

    protected RoleEntity() { }

    public RoleEntity(Long id, String name, String image, String route) {
        this.id = id;
        this.name = name;
        this.image = image;
        this.route = route;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getImage() { return image; }
    public String getRoute() { return route; }
}
