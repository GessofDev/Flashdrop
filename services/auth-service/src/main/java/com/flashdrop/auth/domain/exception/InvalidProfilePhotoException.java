package com.flashdrop.auth.domain.exception;

/** La foto de perfil no es una imagen JPEG o PNG valida. Se mapea a HTTP 400. */
public class InvalidProfilePhotoException extends DomainException {
    public InvalidProfilePhotoException(String message) {
        super("AUTH_PHOTO_INVALID", message);
    }
}
