package com.flashdrop.auth.domain.exception;

public class UserNotFoundException extends DomainException {
    public UserNotFoundException(String message) {
        super(message, "USER_NOT_FOUND");
    }
}
