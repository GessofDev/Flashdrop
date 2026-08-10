package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.supabase.dto;

import com.flashdrop.auth.domain.model.Role;

public record RoleRow(
        Long id,
        String name,
        String route
) {
    public Role toDomain() {
        return new Role(id, name, route);
    }
}
