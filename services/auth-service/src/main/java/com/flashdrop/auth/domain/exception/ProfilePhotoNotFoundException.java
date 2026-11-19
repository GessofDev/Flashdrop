package com.flashdrop.auth.domain.exception;

/** El usuario no tiene foto de perfil, o el archivo ya no existe. Se mapea a HTTP 404. */
public class ProfilePhotoNotFoundException extends DomainException {
    public ProfilePhotoNotFoundException() {
        super("AUTH_PHOTO_NOT_FOUND", "El usuario no tiene foto de perfil");
    }
}
