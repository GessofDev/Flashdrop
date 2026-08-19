package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashdrop.auth.domain.model.RefreshToken;

import java.time.Instant;

public record RefreshTokenRow(
        /** Null al emitir (lo genera la identidad de la columna); presente al
         *  revocar, porque la fila se leyó antes por token_hash. */
        @JsonInclude(JsonInclude.Include.NON_NULL) Long id,
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
