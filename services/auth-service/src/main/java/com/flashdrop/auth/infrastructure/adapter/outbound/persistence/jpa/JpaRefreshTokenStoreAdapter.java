package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.auth.application.port.outbound.RefreshTokenStore;
import com.flashdrop.auth.domain.model.RefreshToken;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.RefreshTokenEntity;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataRefreshTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Refresh tokens contra PostgreSQL. Se guarda solo el hash. */
@Repository
public class JpaRefreshTokenStoreAdapter implements RefreshTokenStore {

    private final SpringDataRefreshTokenRepository tokens;

    public JpaRefreshTokenStoreAdapter(SpringDataRefreshTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public void save(RefreshToken token) {
        tokens.save(new RefreshTokenEntity(token.id(), token.userId(), token.tokenHash(),
                token.expiresAt(), token.revoked()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return tokens.findByTokenHash(tokenHash).map(e -> new RefreshToken(
                e.getId(), e.getUserId(), e.getTokenHash(), e.getExpiresAt(), e.isRevoked()));
    }

    @Override
    @Transactional
    public void revoke(RefreshToken token) {
        // El token llega ya marcado por el caso de uso; se persiste sobre la
        // misma fila, que se localiza por su id.
        save(token.revokedCopy());
    }
}
