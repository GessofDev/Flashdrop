package com.flashdrop.auth.application.port.outbound;

import com.flashdrop.auth.domain.model.Credentials;

import java.util.Optional;

public interface CredentialStore {
    void save(Credentials credentials);
    Optional<Credentials> findByLogin(String login);
    Optional<Credentials> findByUserId(Long userId);
    boolean existsByLogin(String login);
    void updatePassword(Long userId, String passwordHash);
    void updateLogin(Long userId, String newLogin);

    /** Habilita o inhabilita el acceso de la cuenta (login.status). */
    void setActive(Long userId, boolean active);

    /** false si la cuenta esta inhabilitada o ya no existe. */
    boolean isActive(Long userId);
}
