package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserRow(
        /** Se omite del JSON cuando es null para que el INSERT no mande
         *  {@code "id": null} y Postgres pueda generar el id con la
         *  identidad de la columna (ver db/01_schema.sql). */
        @JsonInclude(JsonInclude.Include.NON_NULL) Long id,
        String email,
        String rut,
        String name,
        @JsonProperty("last_name") String lastName,
        String phone,
        String photo,
        @JsonProperty("created_at") Instant createdAt,
        /** Los roles NO son una columna de `users`: viven en la tabla puente
         *  user_has_roles y el repositorio los resuelve en una consulta aparte.
         *  Este campo es solo un portador en memoria, asi que se marca
         *  WRITE_ONLY para que Jackson nunca lo serialice: si sale en el JSON
         *  del INSERT, PostgREST responde
         *  PGRST204 "Could not find the 'roles' column of 'users'". */
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) List<RoleRow> roles
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
