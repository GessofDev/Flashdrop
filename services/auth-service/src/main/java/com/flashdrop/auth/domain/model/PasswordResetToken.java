package com.flashdrop.auth.domain.model;

import java.time.Instant;

/**
 * Enlace de recuperación de contraseña. Igual que el refresh token, solo se
 * guarda el hash: el valor crudo existe únicamente en el correo del usuario.
 */
public record PasswordResetToken(Long userId, String tokenHash, Instant expiresAt) {
}
