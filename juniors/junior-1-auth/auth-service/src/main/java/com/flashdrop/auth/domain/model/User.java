package com.flashdrop.auth.domain.model;

import com.flashdrop.auth.domain.exception.InvalidUserException;
import com.flashdrop.auth.domain.valueobject.Email;

import java.time.Instant;
import java.util.List;


/**
 * Entidad de dominio User. Inmutable y sin dependencias de infraestructura
 * (no importa Spring, JPA ni Hibernate — regla de oro hexagonal).
 */
public class User {

    private final Long id;
    private final Email email;
    private final String rut;
    private final String name;
    private final String lastName;
    private final String phone;
    private final String photo;
    private final List<Role> roles;
    private final Instant createdAt;

    public User(Long id, Email email, String rut, String name, String lastName,
                String phone, String photo, List<Role> roles, Instant createdAt) {
        if (email == null) throw new InvalidUserException("El email es obligatorio");
        this.id = id;
        this.email = email;
        this.rut = rut;
        this.name = name;
        this.lastName = lastName;
        this.phone = phone;
        this.photo = photo;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.createdAt = createdAt;
    }

    public Long id() { return id; }
    public Email email() { return email; }
    public String rut() { return rut; }
    public String name() { return name; }
    public String lastName() { return lastName; }
    public String phone() { return phone; }
    public String photo() { return photo; }
    public List<Role> roles() { return roles; }
    public Instant createdAt() { return createdAt; }

    public List<String> roleNames() {
        return roles.stream().map(Role::name).toList();
    }
}
