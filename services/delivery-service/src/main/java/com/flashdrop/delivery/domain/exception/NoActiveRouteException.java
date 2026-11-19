package com.flashdrop.delivery.domain.exception;

public class NoActiveRouteException extends RuntimeException {

    public NoActiveRouteException(String userId) {
        super("Courier has no active route to report location for userId: " + userId);
    }
}
