package com.flashdrop.catalog.domain.exception;

import java.util.List;

import com.flashdrop.catalog.domain.model.Restaurant;

/** Raised when an operator has multiple stores and omits X-My-Store-Restaurant. */
public class LocalContextRequiredException extends RuntimeException {

    private final List<Restaurant> availableStores;

    public LocalContextRequiredException(List<Restaurant> availableStores) {
        super("Store context header X-My-Store-Restaurant is required when operating multiple stores");
        this.availableStores = List.copyOf(availableStores);
    }

    public List<Restaurant> getAvailableStores() {
        return availableStores;
    }
}
