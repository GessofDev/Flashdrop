package com.flashdrop.catalog.domain.exception;

/**
 * El usuario ya es dueño de otro restaurante (un usuario, un restaurante). Extiende
 * {@link IllegalArgumentException} para no romper a quien ya la captura; el handler REST la
 * traduce a 409.
 */
public class OwnerAlreadyAssignedException extends IllegalArgumentException {

    public OwnerAlreadyAssignedException(String message) {
        super(message);
    }
}
