package com.flashdrop.auth.application.port.outbound;

import com.flashdrop.auth.domain.model.Credentials;

import java.util.Optional;

public interface CredentialStore {
    void save(Credentials credentials);
    Optional<Credentials> findByLogin(String login);
}
