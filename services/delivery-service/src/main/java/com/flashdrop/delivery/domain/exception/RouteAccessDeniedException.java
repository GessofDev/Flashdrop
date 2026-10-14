package com.flashdrop.delivery.domain.exception;

public class RouteAccessDeniedException extends RuntimeException {

    public RouteAccessDeniedException() {
        super("You can only update routes assigned to your delivery profile");
    }
}
