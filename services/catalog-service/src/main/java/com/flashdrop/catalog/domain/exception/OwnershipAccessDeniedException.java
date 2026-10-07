package com.flashdrop.catalog.domain.exception;

public class OwnershipAccessDeniedException extends RuntimeException {

    public OwnershipAccessDeniedException(String message) {
        super(message);
    }
}
