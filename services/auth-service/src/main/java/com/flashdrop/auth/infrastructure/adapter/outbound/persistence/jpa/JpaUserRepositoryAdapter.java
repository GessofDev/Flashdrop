package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.auth.application.port.outbound.UserRepository;
import com.flashdrop.auth.domain.model.Role;
import com.flashdrop.auth.domain.model.User;
import com.flashdrop.auth.domain.valueobject.Email;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.RoleEntity;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.UserEntity;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.UserHasRoleEntity;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataRoleRepository;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataUserHasRoleRepository;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataUserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de usuarios contra PostgreSQL.
 *
 * <p>Los roles se resuelven en dos consultas a traves de la tabla puente en
 * vez de con una asociacion {@code @ManyToMany}: asi {@link User} sigue
 * siendo inmutable y sin dependencias de JPA, que es la regla del dominio.
 */
@Repository
public class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository users;
    private final SpringDataUserHasRoleRepository userRoles;
    private final SpringDataRoleRepository roles;

    public JpaUserRepositoryAdapter(SpringDataUserRepository users,
                                    SpringDataUserHasRoleRepository userRoles,
                                    SpringDataRoleRepository roles) {
        this.users = users;
        this.userRoles = userRoles;
        this.roles = roles;
    }

    @Override
    @Transactional
    public User save(User user) {
        UserEntity guardado = users.save(new UserEntity(
                user.id(), user.email().value(), user.rut(), user.name(),
                user.lastName(), user.phone(), user.photo()));

        if (!user.roles().isEmpty()) {
            // Se reemplaza el conjunto completo: el dominio entrega la lista
            // final de roles, no un delta.
            userRoles.deleteByUserId(guardado.getId());
            userRoles.flush();
            user.roles().forEach(rol ->
                    userRoles.save(new UserHasRoleEntity(guardado.getId(), rol.id())));
        }
        return aDominio(guardado, rolesDe(guardado.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        if (id == null) return Optional.empty();
        return users.findById(id).map(e -> aDominio(e, rolesDe(e.getId())));
    }

    /**
     * Resuelve varios usuarios de una vez. No hidrata roles: el contrato
     * batch (GET /api/internal/users?ids=) no los expone, y traerlos costaria
     * dos consultas mas por usuario — justo el N+1 que ese endpoint evita.
     */
    @Override
    @Transactional(readOnly = true)
    public List<User> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return users.findByIdIn(ids).stream()
                .sorted(Comparator.comparing(UserEntity::getId))
                .map(e -> aDominio(e, List.of()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(Email email) {
        return users.findByEmail(email.value()).map(e -> aDominio(e, rolesDe(e.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(Email email) {
        return users.existsByEmail(email.value());
    }

    private List<Role> rolesDe(Long userId) {
        List<Long> ids = userRoles.findByUserId(userId).stream()
                .map(UserHasRoleEntity::getRoleId)
                .toList();
        if (ids.isEmpty()) return List.of();
        return roles.findByIdIn(ids).stream()
                .map(r -> new Role(r.getId(), r.getName(), r.getRoute()))
                .toList();
    }

    private User aDominio(UserEntity e, List<Role> rolesDelUsuario) {
        return new User(e.getId(), new Email(e.getEmail()), e.getRut(), e.getName(),
                e.getLastName(), e.getPhone(), e.getPhoto(), rolesDelUsuario, e.getCreatedAt());
    }
}
