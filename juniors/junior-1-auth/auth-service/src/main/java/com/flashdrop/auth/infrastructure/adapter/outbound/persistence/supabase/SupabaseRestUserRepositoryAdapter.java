package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase;

import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto.RoleRow;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto.UserHasRoleRow;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto.UserRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class SupabaseRestUserRepositoryAdapter implements UserRepository {

    private final RestClient restClient;

    public SupabaseRestUserRepositoryAdapter(
            @Value("${supabase.url}") String url,
            @Value("${supabase.service-role-key}") String key) {
        this.restClient = SupabaseRestClientFactory.create(url, key);
    }

    @Override
    public User save(User user) {
        UserRow dto = new UserRow(
                user.id(),
                user.email().value(),
                user.rut(),
                user.name(),
                user.lastName(),
                user.phone(),
                user.photo(),
                user.createdAt(),
                null
        );

        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/users")
                        .queryParam("on_conflict", "id")
                        .build())
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .body(dto)
                .retrieve()
                .toBodilessEntity();

        if (!user.roles().isEmpty()) {
            var relations = user.roles().stream()
                    .map(role -> new UserHasRoleRow(user.id(), role.id()))
                    .toList();
            
            restClient.delete()
                    .uri(uriBuilder -> uriBuilder.path("/user_has_roles").queryParam("id_user", "eq." + user.id()).build())
                    .retrieve()
                    .toBodilessEntity();

            restClient.post()
                    .uri("/user_has_roles")
                    .body(relations)
                    .retrieve()
                    .toBodilessEntity();
        }

        return findById(user.id()).orElse(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        // The schema models roles as a many-to-many relationship through the
        // user_has_roles junction table, so PostgREST cannot embed roles via
        // a direct foreign key (`select=*,roles(*)` returns PGRST200). We
        // therefore fetch the user and its roles in two queries and combine
        // the result in Java.
        UserRow[] rows = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/users")
                        .queryParam("id", "eq." + id)
                        .queryParam("select", "*")
                        .build())
                .retrieve()
                .body(UserRow[].class);

        if (rows == null || rows.length == 0) return Optional.empty();

        List<RoleRow> roles = fetchRolesForUser(id);
        return Optional.of(rows[0].withRoles(roles).toDomain());
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        UserRow[] rows = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/users")
                        .queryParam("email", "eq." + email.value())
                        .queryParam("select", "*")
                        .build())
                .retrieve()
                .body(UserRow[].class);

        if (rows == null || rows.length == 0) return Optional.empty();

        List<RoleRow> roles = fetchRolesForUser(rows[0].id());
        return Optional.of(rows[0].withRoles(roles).toDomain());
    }

    /** Fetches the roles assigned to a user through the user_has_roles junction
     *  table. The query is split in two steps because we cannot rely on
     *  PostgREST's nested embed syntax (`select=roles(*)`) to map cleanly
     *  into {@link RoleRow}: the embedded object lives in a sub-field and
     *  Jackson would leave {@code name}/{@code route} as null.
     *
     *  <p>Returns an empty list if the user has no roles or the junction is
     *  empty. */
    private List<RoleRow> fetchRolesForUser(Long userId) {
        // Step 1: read role ids from the junction table.
        UserHasRoleRow[] junctions = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/user_has_roles")
                        .queryParam("id_user", "eq." + userId)
                        .queryParam("select", "id_rol")
                        .build())
                .retrieve()
                .body(UserHasRoleRow[].class);

        if (junctions == null || junctions.length == 0) {
            return List.of();
        }

        String idList = Arrays.stream(junctions)
                .map(j -> String.valueOf(j.roleId()))
                .collect(Collectors.joining(","));

        // Step 2: fetch the roles themselves by id.
        RoleRow[] roles = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/roles")
                        .queryParam("id", "in.(" + idList + ")")
                        .build())
                .retrieve()
                .body(RoleRow[].class);

        return roles == null ? List.of() : Arrays.asList(roles);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return findByEmail(email).isPresent();
    }
}
