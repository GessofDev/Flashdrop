package com.flashdrop.delivery.domain.exception;

public class RouteNotFoundException extends RuntimeException {

    public RouteNotFoundException(String message) {
        super(message);
    }

    public RouteNotFoundException(Long routeId) {
        super("Route not found with ID: " + routeId);
    }
}
