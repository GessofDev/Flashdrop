package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashdrop.auth.domain.model.Credentials;

import java.util.UUID;

public record LoginRow(
        Long id,
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
