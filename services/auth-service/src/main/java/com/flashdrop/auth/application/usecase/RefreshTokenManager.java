package com.flashdrop.auth.application.usecase;

import com.flashdrop.auth.application.port.outbound.OpaqueTokenService;
import com.flashdrop.auth.application.port.outbound.RefreshTokenStore;
import com.flashdrop.auth.domain.exception.InvalidTokenException;
import com.flashdrop.auth.domain.model.RefreshToken;

import java.time.Duration;
import java.time.Instant;

/**
 * Emite, rota y revoca refresh tokens (S-15). Compartido por el login,
 * el refresh y el logout. Se guarda solo el hash del token.
 */
public class RefreshTokenManager {

    private final RefreshTokenStore store;
    private final OpaqueTokenService opaque;
    private final Duration ttl;

    public RefreshTokenManager(RefreshTokenStore store, OpaqueTokenService opaque,
                               Duration ttl) {
        this.store = store;
        this.opaque = opaque;
        this.ttl = ttl;
    }

    /** Emite un refresh token nuevo. El id va en null a propósito: lo genera
     *  la identidad de `refresh_tokens.id` (ver db/01_schema.sql). Nada
     *  posterior necesita ese id — la rotación y el logout localizan la fila
     *  por `token_hash`. */
    public String issueFor(Long userId) {
        String raw = opaque.generate();
        store.save(new RefreshToken(null, userId, opaque.hash(raw),
                Instant.now().plus(ttl), false));
        return raw;
    }

    /** Valida y rota: revoca el actual y emite uno nuevo. Devuelve el userId + nuevo token crudo. */
    public Rotation rotate(String rawToken) {
        RefreshToken current = store.findByTokenHash(opaque.hash(rawToken))
                .orElseThrow(InvalidTokenException::new);
        if (!current.isUsable(Instant.now())) {
            throw new InvalidTokenException();
        }
        store.revoke(current.revokedCopy());
        return new Rotation(current.userId(), issueFor(current.userId()));
    }

    /** Revoca el token (logout). Idempotente. */
    public void revoke(String rawToken) {
        store.findByTokenHash(opaque.hash(rawToken)).ifPresent(t -> store.revoke(t.revokedCopy()));
    }

    public record Rotation(Long userId, String newRefreshToken) {}
}
