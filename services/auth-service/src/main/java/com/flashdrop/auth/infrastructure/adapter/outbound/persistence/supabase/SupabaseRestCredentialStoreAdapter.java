package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase;

import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto.LoginRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Repository
@Profile("supabase")
public class SupabaseRestCredentialStoreAdapter implements CredentialStore {

    private final RestClient restClient;

    public SupabaseRestCredentialStoreAdapter(
            @Value("${supabase.url}") String url,
            @Value("${supabase.service-role-key}") String key) {
        this.restClient = SupabaseRestClientFactory.create(url, key);
    }

    @Override
    public void save(Credentials credentials) {
        Integer statusInt = "ACTIVE".equalsIgnoreCase(credentials.status()) ? 1 : 0;
        LoginRow dto = new LoginRow(
                credentials.id(),
                credentials.login(),
                credentials.passwordHash(),
                statusInt,
                credentials.userId()
        );

        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/login")
                        .queryParam("on_conflict", "id")
                        .build())
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .body(dto)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public Optional<Credentials> findByLogin(String login) {
        LoginRow[] rows = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/login")
                        .queryParam("login", "eq." + login)
                        .queryParam("select", "*")
                        .build())
                .retrieve()
                .body(LoginRow[].class);

        if (rows == null || rows.length == 0) return Optional.empty();
        return Optional.of(rows[0].toDomain());
    }
}
