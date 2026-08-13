package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase;

import com.flashdrop.auth.application.port.outbound.RoleRepository;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto.RoleRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Repository
public class SupabaseRestRoleRepositoryAdapter implements RoleRepository {

    private final RestClient restClient;

    public SupabaseRestRoleRepositoryAdapter(
            @Value("${supabase.url}") String url,
            @Value("${supabase.service-role-key}") String key) {
        this.restClient = SupabaseRestClientFactory.create(url, key);
    }

    @Override
    public Optional<Role> findByName(String name) {
        RoleRow[] rows = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/roles")
                        .queryParam("name", "eq." + name)
                        .queryParam("select", "*")
                        .build())
                .retrieve()
                .body(RoleRow[].class);

        if (rows == null || rows.length == 0) {
            return Optional.empty();
        }
        return Optional.of(rows[0].toDomain());
    }
}
