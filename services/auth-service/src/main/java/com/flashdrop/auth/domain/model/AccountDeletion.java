package com.flashdrop.auth.domain.model;

import java.time.Instant;

/**
 * Cuenta en proceso de eliminacion (F5). Guarda que servicios ya borraron sus datos para que un
 * reintento continue donde quedo. No contiene datos personales.
 */
public record AccountDeletion(
        Long userId,
        Status status,
        int attempts,
        boolean ordersDone,
        boolean deliveryDone,
        boolean catalogDone,
        Instant nextAttemptAt,
        String lastError
) {

    public enum Status { PENDING, FAILED }

    public AccountDeletion withOrdersDone() {
        return new AccountDeletion(userId, status, attempts, true, deliveryDone, catalogDone, nextAttemptAt, lastError);
    }

    public AccountDeletion withDeliveryDone() {
        return new AccountDeletion(userId, status, attempts, ordersDone, true, catalogDone, nextAttemptAt, lastError);
    }

    public AccountDeletion withCatalogDone() {
        return new AccountDeletion(userId, status, attempts, ordersDone, deliveryDone, true, nextAttemptAt, lastError);
    }

    public AccountDeletion retryAt(Instant next, String error) {
        return new AccountDeletion(userId, status, attempts + 1, ordersDone, deliveryDone, catalogDone, next, error);
    }

    public AccountDeletion failed(String error) {
        return new AccountDeletion(userId, Status.FAILED, attempts + 1, ordersDone, deliveryDone, catalogDone,
                nextAttemptAt, error);
    }
}
