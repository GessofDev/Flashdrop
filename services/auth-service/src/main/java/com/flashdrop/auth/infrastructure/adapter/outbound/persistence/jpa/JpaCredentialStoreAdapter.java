package com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa;

import com.flashdrop.auth.application.port.outbound.CredentialStore;
import com.flashdrop.auth.domain.model.Credentials;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.entity.LoginEntity;
import com.flashdrop.auth.infrastructure.adapter.outbound.persistence.jpa.repository.SpringDataLoginRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Credenciales contra PostgreSQL. El estado se guarda como el entero del
 *  esquema (1 activo / 0 inactivo) y se traduce al dominio como texto. */
@Repository
public class JpaCredentialStoreAdapter implements CredentialStore {

    private static final String ACTIVO = "ACTIVE";

    private final SpringDataLoginRepository logins;

    public JpaCredentialStoreAdapter(SpringDataLoginRepository logins) {
        this.logins = logins;
    }

    @Override
    @Transactional
    public void save(Credentials credentials) {
        logins.save(new LoginEntity(
                credentials.id(),
                credentials.login(),
                credentials.passwordHash(),
                credentials.userId(),
                ACTIVO.equalsIgnoreCase(credentials.status()) ? 1 : 0));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Credentials> findByLogin(String login) {
        return logins.findByLogin(login).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Credentials> findByUserId(Long userId) {
        return logins.findByUserId(userId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByLogin(String login) {
        return logins.existsByLogin(login);
    }

    @Override
    @Transactional
    public void setActive(Long userId, boolean active) {
        logins.updateStatus(userId, active ? 1 : 0);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(Long userId) {
        return logins.findByUserId(userId).map(e -> Integer.valueOf(1).equals(e.getStatus())).orElse(false);
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, String passwordHash) {
        logins.updatePassword(userId, passwordHash);
    }

    @Override
    @Transactional
    public void updateLogin(Long userId, String newLogin) {
        if (logins.updateLogin(userId, newLogin) == 0) {
            // Sin esta fila, `users.email` quedaria con el email nuevo y
            // `login.login` con el viejo: el usuario no podria entrar.
            throw new IllegalStateException("El usuario " + userId + " no tiene credenciales de acceso");
        }
    }

    private Credentials toDomain(LoginEntity e) {
        return new Credentials(
                e.getId(), e.getUserId(), e.getLogin(), e.getPassword(),
                Integer.valueOf(1).equals(e.getStatus()) ? ACTIVO : "INACTIVE");
    }
}
