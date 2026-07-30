package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record UserHasRoleRow(
        @JsonProperty("id_user") Long userId,
        @JsonProperty("id_rol") Long roleId
) {
}
