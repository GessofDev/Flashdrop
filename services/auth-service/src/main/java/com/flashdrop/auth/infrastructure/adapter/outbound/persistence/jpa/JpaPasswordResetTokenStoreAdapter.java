package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.auth.application.port.outbound.PasswordResetTokenStore;
import com.flashdrop.auth.domain.model.PasswordResetToken;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.PasswordResetTokenEntity;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataPasswordResetTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** Tokens de recuperación contra PostgreSQL. Se guarda solo el hash. */
@Repository
public class JpaPasswordResetTokenStoreAdapter implements PasswordResetTokenStore {

    private final SpringDataPasswordResetTokenRepository tokens;

    public JpaPasswordResetTokenStoreAdapter(SpringDataPasswordResetTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public void save(PasswordResetToken token) {
        tokens.save(new PasswordResetTokenEntity(token.userId(), token.tokenHash(), token.expiresAt()));
    }

    @Override
    @Transactional
    public Optional<Long> consume(String tokenHash, Instant now) {
        // El UPDATE condicional es lo que hace atómico el único uso.
        if (tokens.markUsed(tokenHash, now) == 0) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(tokenHash).map(PasswordResetTokenEntity::getUserId);
    }

    @Override
    @Transactional
    public void invalidateAllForUser(Long userId, Instant now) {
        tokens.markAllUsed(userId, now);
    }
}
