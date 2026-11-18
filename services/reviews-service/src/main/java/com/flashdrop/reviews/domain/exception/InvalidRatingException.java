package com.flashdrop.reviews.domain.exception;

/**
 * La calificación está fuera del rango 1..5 (S5). Se traduce a HTTP 400.
 */
public class InvalidRatingException extends RuntimeException {

    public InvalidRatingException(String message) {
        super(message);
    }
}
