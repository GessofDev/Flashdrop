package com.flashdrop.auth.application.port.outbound;

import com.flashdrop.auth.domain.model.EmailChangeToken;

import java.time.Instant;
import java.util.Optional;

public interface EmailChangeTokenStore {
    void save(EmailChangeToken token);

    /**
     * Marks the token as used and returns its userId atomically.
     * Empty if the token does not exist, expired, or was already used.
     */
    Optional<Long> consume(String tokenHash, Instant now);

    /** Invalidates any pending email change tokens for the user. */
    void invalidateAllForUser(Long userId, Instant now);
}
