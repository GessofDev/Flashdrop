package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase;

import com.flashdrop.auth.application.port.outbound.RefreshTokenStore;
import com.flashdrop.auth.domain.model.RefreshToken;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto.RefreshTokenRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;

@Repository
@Profile("supabase")
public class SupabaseRestRefreshTokenStoreAdapter implements RefreshTokenStore {

    private final RestClient restClient;

    public SupabaseRestRefreshTokenStoreAdapter(
            @Value("${supabase.url}") String url,
            @Value("${supabase.service-role-key}") String key) {
        this.restClient = SupabaseRestClientFactory.create(url, key);
    }

    @Override
    public void save(RefreshToken token) {
        RefreshTokenRow dto = new RefreshTokenRow(
                token.id(),
                token.userId(),
                token.tokenHash(),
                token.expiresAt(),
                token.revoked(),
                Instant.now()
        );

        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/refresh_tokens")
                        .queryParam("on_conflict", "id")
                        .build())
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .body(dto)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        RefreshTokenRow[] rows = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/refresh_tokens")
                        .queryParam("token_hash", "eq." + tokenHash)
                        .queryParam("select", "*")
                        .build())
                .retrieve()
                .body(RefreshTokenRow[].class);

        if (rows == null || rows.length == 0) return Optional.empty();
        return Optional.of(rows[0].toDomain());
    }

    @Override
    public void revoke(RefreshToken token) {
        RefreshToken revokedToken = token.revokedCopy();
        save(revokedToken);
    }
}
