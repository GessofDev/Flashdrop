package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.auth.application.port.outbound.RoleRepository;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataRoleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Profile("postgres")
public class JpaRoleRepositoryAdapter implements RoleRepository {

    private final SpringDataRoleRepository roles;

    public JpaRoleRepositoryAdapter(SpringDataRoleRepository roles) {
        this.roles = roles;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByName(String name) {
        return roles.findByName(name)
                .map(r -> new Role(r.getId(), r.getName(), r.getRoute()));
    }
}
