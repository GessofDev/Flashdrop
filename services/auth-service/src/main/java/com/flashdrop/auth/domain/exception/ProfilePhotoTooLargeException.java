package com.flashdrop.auth.domain.exception;

/** La foto de perfil supera el peso o las dimensiones permitidas. Se mapea a HTTP 413. */
public class ProfilePhotoTooLargeException extends DomainException {
    public ProfilePhotoTooLargeException(String message) {
        super("AUTH_PHOTO_TOO_LARGE", message);
    }
}
