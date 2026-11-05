package com.flashdrop.auth.application.port.outbound;

import com.flashdrop.auth.domain.model.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenStore {
    void save(PasswordResetToken token);

    /**
     * Marca el token como usado y devuelve su usuario. Es atómico: con dos
     * peticiones simultáneas solo una lo consigue, así el enlace es de un
     * solo uso. Vacío si no existe, venció o ya se usó.
     */
    Optional<Long> consume(String tokenHash, Instant now);

    /** Deja sin efecto los enlaces pendientes del usuario. */
    void invalidateAllForUser(Long userId, Instant now);
}
