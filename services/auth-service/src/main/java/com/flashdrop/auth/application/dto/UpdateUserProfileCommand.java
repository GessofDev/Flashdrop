package com.flashdrop.auth.application.dto;

/**
 * Campos editables del perfil. El email y el rut no están: no se cambian por esta vía.
 * La foto tampoco: se sube por {@code POST /auth/profile/photo} (F10).
 */
public record UpdateUserProfileCommand(
        String name,
        String lastName,
        String phone
) {
}
