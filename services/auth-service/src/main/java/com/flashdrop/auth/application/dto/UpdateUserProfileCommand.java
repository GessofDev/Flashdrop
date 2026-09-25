package com.flashdrop.auth.application.dto;

/** Campos editables del perfil. El email y el rut no están: no se cambian por esta vía. */
public record UpdateUserProfileCommand(
        String name,
        String lastName,
        String phone,
        String photo
) {
}
