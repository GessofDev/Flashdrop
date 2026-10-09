package com.flashdrop.delivery.domain.exception;

/**
 * Thrown by {@code CreateDeliveryPersonUseCase} when the userId from the
 * JWT already has a delivery profile. Mapped to HTTP 409 by
 * {@code GlobalExceptionHandler} so the client gets a clear "you already
 * signed up" signal instead of a generic 500.
 */
public class DeliveryPersonAlreadyExistsException extends RuntimeException {

    public DeliveryPersonAlreadyExistsException(String userId) {
        super("Delivery person profile already exists for user ID: " + userId);
    }
}
