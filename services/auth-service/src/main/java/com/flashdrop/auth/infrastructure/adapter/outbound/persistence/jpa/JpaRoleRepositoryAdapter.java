package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.auth.application.port.outbound.RoleRepository;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.RoleEntity;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataRoleRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaRoleRepositoryAdapter implements RoleRepository {

    private final SpringDataRoleRepository roles;

    public JpaRoleRepositoryAdapter(SpringDataRoleRepository roles) {
        this.roles = roles;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByName(String name) {
        // Sin distinguir mayusculas: "cliente" y "Cliente" son el mismo rol,
        // y el nombre canonico es el de la tabla.
        return roles.findByNameIgnoreCase(name)
                .map(r -> new Role(r.getId(), r.getName(), r.getRoute()));
    }

    /** El catalogo viene ordenado por id para que la respuesta sea estable. */
    @Override
    @Transactional(readOnly = true)
    public List<Role> findAll() {
        return roles.findAll().stream()
                .sorted(Comparator.comparing(RoleEntity::getId))
                .map(r -> new Role(r.getId(), r.getName(), r.getRoute()))
                .toList();
    }
}
