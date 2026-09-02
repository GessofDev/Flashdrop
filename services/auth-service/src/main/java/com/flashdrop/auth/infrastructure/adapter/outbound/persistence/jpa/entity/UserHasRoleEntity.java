package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.*;

/** Tabla puente `user_has_roles`.
 *
 *  <p>Se modela como entidad propia y no como {@code @ManyToMany} a
 *  proposito: el dominio trata los roles como una lista de valores del
 *  usuario, no como una asociacion navegable en ambos sentidos, y una
 *  coleccion administrada por JPA obligaria a que {@code User} dejara de
 *  ser inmutable. */
@Entity
@Table(name = "user_has_roles",
       uniqueConstraints = @UniqueConstraint(columnNames = {"id_user", "id_rol"}))
public class UserHasRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_user", nullable = false)
    private Long userId;

    @Column(name = "id_rol", nullable = false)
    private Long roleId;

    protected UserHasRoleEntity() { }

    public UserHasRoleEntity(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getRoleId() { return roleId; }
}
