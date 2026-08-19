package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashdrop.auth.domain.model.Credentials;

public record LoginRow(
        /** Igual que en {@link UserRow}: se omite si es null para dejar que
         *  la identidad de la columna genere el id en el INSERT. */
        @JsonInclude(JsonInclude.Include.NON_NULL) Long id,
        String login,
        String password,
        Integer status,
        @JsonProperty("id_users") Long userId
) {
    public Credentials toDomain() {
        String statusStr = (status != null && status == 1) ? "ACTIVE" : "INACTIVE";
        return new Credentials(id, userId, login, password, statusStr);
    }
}
