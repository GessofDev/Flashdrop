package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** Tabla `users`. Identidad y datos de perfil; el hash de contrasena vive
 *  aparte, en `login`, para que un SELECT de perfil nunca lo arrastre. */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String rut;
    private String name;

    @Column(name = "last_name")
    private String lastName;

    @Column(unique = true)
    private String phone;

    private String photo;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected UserEntity() { }

    public UserEntity(Long id, String email, String rut, String name, String lastName,
                      String phone, String photo) {
        this.id = id;
        this.email = email;
        this.rut = rut;
        this.name = name;
        this.lastName = lastName;
        this.phone = phone;
        this.photo = photo;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getRut() { return rut; }
    public String getName() { return name; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public String getPhoto() { return photo; }
    public Instant getCreatedAt() { return createdAt; }
}
