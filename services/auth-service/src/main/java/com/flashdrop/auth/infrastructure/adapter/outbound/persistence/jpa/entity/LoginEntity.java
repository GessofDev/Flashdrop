package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.*;

/** Tabla `login`. Credenciales de acceso: el password es siempre un hash
 *  bcrypt, nunca texto plano. */
@Entity
@Table(name = "login")
public class LoginEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login", nullable = false, unique = true)
    private String login;

    @Column(nullable = false)
    private String password;

    @Column(name = "id_users", nullable = false, unique = true)
    private Long userId;

    /** 1 = ACTIVE, 0 = INACTIVE. Se conserva el entero del esquema original. */
    @Column(nullable = false)
    private Integer status;

    protected LoginEntity() { }

    public LoginEntity(Long id, String login, String password, Long userId, Integer status) {
        this.id = id;
        this.login = login;
        this.password = password;
        this.userId = userId;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getLogin() { return login; }
    public String getPassword() { return password; }
    public Long getUserId() { return userId; }
    public Integer getStatus() { return status; }
}
