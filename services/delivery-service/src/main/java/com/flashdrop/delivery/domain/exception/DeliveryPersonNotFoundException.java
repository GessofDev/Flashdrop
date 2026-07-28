package com.flashdrop.delivery.domain.exception;

public class DeliveryPersonNotFoundException extends RuntimeException {

    public DeliveryPersonNotFoundException(String message) {
        super(message);
    }

    public DeliveryPersonNotFoundException(Long userId) {
        super("Delivery person not found for user ID: " + userId);
    }
}
