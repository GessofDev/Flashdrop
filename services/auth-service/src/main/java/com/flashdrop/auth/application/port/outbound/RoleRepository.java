package com.flashdrop.auth.application.port.outbound;

import com.flashdrop.auth.domain.model.Role;

import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    /**
     * Busca un rol por nombre sin distinguir mayusculas: "cliente" y
     * "Cliente" son el mismo rol. El nombre canonico es el de la tabla.
     */
    Optional<Role> findByName(String name);

    /** Catalogo completo de roles. La tabla es pequena y estatica. */
    List<Role> findAll();
}
