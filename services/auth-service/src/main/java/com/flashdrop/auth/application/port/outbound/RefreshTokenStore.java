package com.flashdrop.auth.application.port.outbound;

import com.flashdrop.auth.domain.model.RefreshToken;

import java.util.Optional;

public interface RefreshTokenStore {
    void save(RefreshToken token);
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void revoke(RefreshToken token);
}
