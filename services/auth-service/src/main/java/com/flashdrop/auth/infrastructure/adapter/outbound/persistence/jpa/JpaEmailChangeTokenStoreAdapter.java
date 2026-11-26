package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.auth.application.port.outbound.EmailChangeTokenStore;
import com.flashdrop.auth.domain.model.EmailChangeToken;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.EmailChangeTokenEntity;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataEmailChangeTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaEmailChangeTokenStoreAdapter implements EmailChangeTokenStore {

    private final SpringDataEmailChangeTokenRepository tokens;

    public JpaEmailChangeTokenStoreAdapter(SpringDataEmailChangeTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public void save(EmailChangeToken token) {
        tokens.save(new EmailChangeTokenEntity(token.userId(), token.tokenHash(), token.expiresAt()));
    }

    @Override
    @Transactional
    public Optional<Long> consume(String tokenHash, Instant now) {
        if (tokens.markUsed(tokenHash, now) == 0) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(tokenHash).map(EmailChangeTokenEntity::getUserId);
    }

    @Override
    @Transactional
    public void invalidateAllForUser(Long userId, Instant now) {
        tokens.markAllUsed(userId, now);
    }
}
