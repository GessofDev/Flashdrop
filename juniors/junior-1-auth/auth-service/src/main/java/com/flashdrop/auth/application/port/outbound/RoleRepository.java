package com.flashdrop.auth.application.port.outbound;

import com.flashdrop.auth.domain.model.Role;

import java.util.Optional;

public interface RoleRepository {
    Optional<Role> findByName(String name);
}
