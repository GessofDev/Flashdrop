package com.flashdrop.delivery.domain.exception;

public class CourierLocationNotFoundException extends RuntimeException {

    public CourierLocationNotFoundException(Long routeId) {
        super("No active courier location available for route: " + routeId);
    }
}
