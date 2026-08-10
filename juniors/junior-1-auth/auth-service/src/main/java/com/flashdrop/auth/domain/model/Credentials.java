package com.flashdrop.auth.domain.model;

/**
 * Credenciales de acceso. Nunca salen del servicio: el passwordHash
 * jamás se expone en una respuesta.
 */
public record Credentials(Long id, Long userId, String login, String passwordHash, String status) {

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
