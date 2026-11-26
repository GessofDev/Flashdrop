package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** Table `email_change_tokens`. Only the token hash is persisted. */
@Entity
@Table(name = "email_change_tokens")
public class EmailChangeTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected EmailChangeTokenEntity() { }

    public EmailChangeTokenEntity(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public Long getUserId() { return userId; }
}
