package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashdrop.auth.domain.model.RefreshToken;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenRow(
        Long id,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("token_hash") String tokenHash,
        @JsonProperty("expires_at") Instant expiresAt,
        boolean revoked,
        @JsonProperty("created_at") Instant createdAt
) {
    public RefreshToken toDomain() {
        return new RefreshToken(id, userId, tokenHash, expiresAt, revoked);
    }
}
