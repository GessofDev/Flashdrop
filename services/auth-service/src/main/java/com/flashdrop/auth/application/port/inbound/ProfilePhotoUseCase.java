package com.flashdrop.auth.application.port.inbound;

import com.flashdrop.auth.application.dto.UserProfile;

/** Foto del perfil propio: subir o reemplazar, leer y quitar (F10). */
public interface ProfilePhotoUseCase {

    UserProfile upload(Long userId, byte[] content, String declaredContentType);

    /** JPEG de la foto del usuario. */
    byte[] get(Long userId);

    void delete(Long userId);
}
