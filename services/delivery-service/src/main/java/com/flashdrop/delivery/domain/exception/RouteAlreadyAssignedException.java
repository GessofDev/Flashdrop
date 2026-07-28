package com.flashdrop.delivery.domain.exception;

public class RouteAlreadyAssignedException extends RuntimeException {

    public RouteAlreadyAssignedException(String message) {
        super(message);
    }

    public RouteAlreadyAssignedException(Long orderId) {
        super("Route already assigned for order ID: " + orderId);
    }
}
