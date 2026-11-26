package com.flashdrop.auth.domain.model;

import java.time.Instant;

/**
 * Confirmation token for login email change. Only the SHA-256 hash is stored;
 * the raw token exists solely in the email sent to the user's current address.
 */
public record EmailChangeToken(Long userId, String tokenHash, Instant expiresAt) {
}
