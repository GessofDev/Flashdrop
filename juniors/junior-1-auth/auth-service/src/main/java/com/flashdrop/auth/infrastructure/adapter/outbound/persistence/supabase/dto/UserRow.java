package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserRow(
        Long id,
        String email,
        String rut,
        String name,
        @JsonProperty("last_name") String lastName,
        String phone,
        String photo,
        @JsonProperty("created_at") Instant createdAt,
        List<RoleRow> roles
) {
    /** Returns a copy of this row with the given roles. Used by the repository
     *  when roles are fetched separately through the user_has_roles junction
     *  table (the schema is many-to-many, so PostgREST cannot embed roles via
     *  a direct foreign key). */
    public UserRow withRoles(List<RoleRow> newRoles) {
        return new UserRow(id, email, rut, name, lastName, phone, photo, createdAt, newRoles);
    }

    public User toDomain() {
        List<Role> domainRoles = roles == null ? List.of() : roles.stream().map(RoleRow::toDomain).toList();
        return new User(id, new Email(email), rut, name, lastName, phone, photo, domainRoles, createdAt);
    }
}
